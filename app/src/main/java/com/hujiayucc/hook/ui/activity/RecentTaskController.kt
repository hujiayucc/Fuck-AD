package com.hujiayucc.hook.ui.activity

import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.util.Log
import androidx.core.content.edit
import com.hujiayucc.hook.autoskip.AutoSkipAtomicFile
import com.hujiayucc.hook.data.Data.prefsBridge
import java.io.File

internal object RecentTaskController {
    private const val TAG = "RecentTaskController"
    private const val KEY_HIDE_FROM_RECENTS = "hideFromRecents"
    // SharedPreferences instances are cached per process; both processes read this atomic file instead.
    private const val STATE_FILE_NAME = "recent_task_visibility"
    private const val STATE_HIDDEN = "1"
    private const val STATE_VISIBLE = "0"

    fun isHideFromRecentsEnabled(activity: Activity): Boolean {
        readCrossProcessState(activity)?.let { return it }

        val legacyValue = runCatching {
            activity.prefsBridge.getBoolean(KEY_HIDE_FROM_RECENTS, false)
        }.onFailure { error ->
            Log.w(TAG, "Unable to read legacy recent-task visibility preference", error)
        }.getOrDefault(false)

        if (Application.getProcessName() == activity.packageName) {
            writeCrossProcessState(activity, legacyValue)
        }
        return legacyValue
    }

    fun setHideFromRecentsEnabled(activity: Activity, enabled: Boolean) {
        writeCrossProcessState(activity, enabled)
        runCatching {
            activity.prefsBridge.edit(commit = true) {
                putBoolean(KEY_HIDE_FROM_RECENTS, enabled)
            }
        }.onFailure { error ->
            Log.w(TAG, "Unable to save legacy recent-task visibility preference", error)
        }
    }

    fun applyConfiguredState(activity: Activity) {
        setExcludedFromRecents(activity, isHideFromRecentsEnabled(activity))
    }

    fun setExcludedFromRecents(activity: Activity, excluded: Boolean) {
        runCatching {
            val activityManager = activity.getSystemService(ActivityManager::class.java)
            activityManager?.appTasks
                ?.firstOrNull { appTask -> appTask.taskInfo?.taskId == activity.taskId }
                ?.setExcludeFromRecents(excluded)
        }.onFailure { error ->
            Log.w(TAG, "Unable to update recent-task visibility", error)
        }
    }

    private fun readCrossProcessState(activity: Activity): Boolean? {
        return runCatching {
            val file = stateFile(activity)
            if (!AutoSkipAtomicFile.exists(file)) return@runCatching null
            when (AutoSkipAtomicFile.readText(file).trim()) {
                STATE_HIDDEN -> true
                STATE_VISIBLE -> false
                else -> null
            }
        }.onFailure { error ->
            Log.w(TAG, "Unable to read cross-process recent-task visibility", error)
        }.getOrNull()
    }

    private fun writeCrossProcessState(activity: Activity, enabled: Boolean) {
        runCatching {
            AutoSkipAtomicFile.writeText(
                stateFile(activity),
                if (enabled) STATE_HIDDEN else STATE_VISIBLE
            )
        }.onFailure { error ->
            Log.w(TAG, "Unable to save cross-process recent-task visibility", error)
        }
    }

    private fun stateFile(activity: Activity): File {
        return File(activity.applicationContext.noBackupFilesDir, STATE_FILE_NAME)
    }
}

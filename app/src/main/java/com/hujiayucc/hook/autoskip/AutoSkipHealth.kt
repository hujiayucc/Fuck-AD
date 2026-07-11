package com.hujiayucc.hook.autoskip

import android.content.Context
import java.io.File
import org.json.JSONObject

object AutoSkipHealth {
    private const val HEALTH_FILE = "autoskip_health.json"
    private const val TEMP_SUFFIX = ".tmp"
    private const val MAX_ERROR_LENGTH = 160

    private val lock = Any()
    private var cachedState = AutoSkipHealthState()

    fun stateFile(context: Context): File {
        return File(context.applicationContext.noBackupFilesDir, HEALTH_FILE)
    }

    fun markConnected(context: Context, engineGeneration: Int) {
        val now = System.currentTimeMillis()
        update(context) { state ->
            state.copy(
                serviceConnected = true,
                lastConnectedAt = now,
                lastHeartbeatAt = now,
                engineGeneration = engineGeneration,
                lastDisconnectReason = ""
            )
        }
    }

    fun markHeartbeat(context: Context, engineGeneration: Int) {
        val now = System.currentTimeMillis()
        update(context) { state ->
            state.copy(
                serviceConnected = true,
                lastHeartbeatAt = now,
                engineGeneration = engineGeneration
            )
        }
    }

    fun markEvent(context: Context, engineGeneration: Int) {
        val now = System.currentTimeMillis()
        update(context) { state ->
            state.copy(
                serviceConnected = true,
                lastHeartbeatAt = now,
                lastEventAt = now,
                engineGeneration = engineGeneration
            )
        }
    }

    fun markDisconnected(context: Context, reason: String) {
        update(context) { state ->
            state.copy(
                serviceConnected = false,
                lastDisconnectReason = reason.take(MAX_ERROR_LENGTH)
            )
        }
    }

    fun recordError(context: Context, stage: String, error: Throwable, engineGeneration: Int) {
        val now = System.currentTimeMillis()
        val message = buildString {
            append(stage)
            append(": ")
            append(error.javaClass.simpleName)
            error.message?.takeIf { it.isNotBlank() }?.let { detail ->
                append(": ")
                append(detail)
            }
        }.take(MAX_ERROR_LENGTH)
        update(context) { state ->
            state.copy(
                serviceConnected = true,
                lastHeartbeatAt = now,
                engineGeneration = engineGeneration,
                lastError = message
            )
        }
    }

    fun read(context: Context): AutoSkipHealthState? {
        return runCatching {
            val file = stateFile(context)
            if (!file.isFile) return null
            AutoSkipHealthState.fromJson(JSONObject(file.readText()))
        }.getOrNull()
    }

    private fun update(context: Context, transform: (AutoSkipHealthState) -> AutoSkipHealthState) {
        synchronized(lock) {
            cachedState = transform(cachedState)
            writeState(context, cachedState)
        }
    }

    private fun writeState(context: Context, state: AutoSkipHealthState) {
        runCatching {
            val file = stateFile(context)
            file.parentFile?.mkdirs()
            val tempFile = File(file.parentFile, file.name + TEMP_SUFFIX)
            tempFile.writeText(state.toJson().toString())
            if (!tempFile.renameTo(file)) {
                file.writeText(state.toJson().toString())
                tempFile.delete()
            }
        }
    }
}

data class AutoSkipHealthState(
    val serviceConnected: Boolean = false,
    val lastConnectedAt: Long = 0L,
    val lastHeartbeatAt: Long = 0L,
    val lastEventAt: Long = 0L,
    val engineGeneration: Int = 0,
    val lastError: String = "",
    val lastDisconnectReason: String = ""
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("serviceConnected", serviceConnected)
            put("lastConnectedAt", lastConnectedAt)
            put("lastHeartbeatAt", lastHeartbeatAt)
            put("lastEventAt", lastEventAt)
            put("engineGeneration", engineGeneration)
            put("lastError", lastError)
            put("lastDisconnectReason", lastDisconnectReason)
        }
    }

    companion object {
        fun fromJson(obj: JSONObject): AutoSkipHealthState {
            return AutoSkipHealthState(
                serviceConnected = obj.optBoolean("serviceConnected", false),
                lastConnectedAt = obj.optLong("lastConnectedAt", 0L),
                lastHeartbeatAt = obj.optLong("lastHeartbeatAt", 0L),
                lastEventAt = obj.optLong("lastEventAt", 0L),
                engineGeneration = obj.optInt("engineGeneration", 0),
                lastError = obj.optString("lastError"),
                lastDisconnectReason = obj.optString("lastDisconnectReason")
            )
        }
    }
}

package com.hujiayucc.hook.autoskip

import android.content.Context
import java.io.File
import java.util.concurrent.Executors
import org.json.JSONObject

object AutoSkipHealth {
    private const val HEALTH_FILE = "autoskip_health.json"
    private const val MAX_ERROR_LENGTH = 160

    private val lock = Any()
    private val writer = Executors.newSingleThreadExecutor { task ->
        Thread(task, "AutoSkipHealthWriter").apply { isDaemon = true }
    }
    private var cachedState = AutoSkipHealthState()
    private var pendingState: AutoSkipHealthState? = null
    private var pendingContext: Context? = null
    private var writeScheduled = false
    private var lastWrittenJson = ""

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

    fun markEvent(context: Context, engineGeneration: Int, packageName: String = "") {
        val now = System.currentTimeMillis()
        update(context) { state ->
            state.copy(
                serviceConnected = true,
                lastHeartbeatAt = now,
                lastEventAt = now,
                lastEventPackageName = packageName.ifBlank { state.lastEventPackageName },
                engineGeneration = engineGeneration
            )
        }
    }

    fun markNotificationEvent(
        context: Context,
        engineGeneration: Int,
        eventType: Int,
        eventPackageName: String,
        titlePackageName: String
    ) {
        val now = System.currentTimeMillis()
        update(context) { state ->
            state.copy(
                serviceConnected = true,
                lastHeartbeatAt = now,
                lastEventAt = now,
                lastRawEventType = eventType,
                lastRawEventPackageName = eventPackageName,
                lastTitlePackageName = titlePackageName.ifBlank { state.lastTitlePackageName },
                lastEventPackageName = titlePackageName.ifBlank { state.lastEventPackageName },
                engineGeneration = engineGeneration
            )
        }
    }

    fun markCurrentPackage(context: Context, engineGeneration: Int, packageName: String) {
        if (packageName.isBlank() || packageName == cachedState.lastEventPackageName) return
        val now = System.currentTimeMillis()
        update(context) { state ->
            state.copy(
                serviceConnected = true,
                lastHeartbeatAt = now,
                lastEventPackageName = packageName,
                lastTitlePackageName = packageName,
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

    fun clearError(context: Context, engineGeneration: Int) {
        update(context) { state ->
            if (state.lastError.isBlank()) {
                state
            } else {
                state.copy(
                    engineGeneration = engineGeneration,
                    lastError = ""
                )
            }
        }
    }

    fun read(context: Context): AutoSkipHealthState? {
        return runCatching {
            val file = stateFile(context)
            if (!AutoSkipAtomicFile.exists(file)) return null
            AutoSkipHealthState.fromJson(JSONObject(AutoSkipAtomicFile.readText(file)))
        }.getOrNull()
    }

    private fun update(context: Context, transform: (AutoSkipHealthState) -> AutoSkipHealthState) {
        synchronized(lock) {
            val nextState = transform(cachedState)
            if (nextState == cachedState) return
            cachedState = nextState
            pendingState = nextState
            pendingContext = context.applicationContext
            if (!writeScheduled) {
                writeScheduled = true
                writer.execute(::drainPendingWrites)
            }
        }
    }

    private fun drainPendingWrites() {
        while (true) {
            val context: Context
            val state: AutoSkipHealthState
            synchronized(lock) {
                state = pendingState ?: run {
                    writeScheduled = false
                    return
                }
                context = pendingContext ?: run {
                    pendingState = null
                    writeScheduled = false
                    return
                }
                pendingState = null
                pendingContext = null
            }
            val json = state.toJson().toString()
            if (json != lastWrittenJson && writeState(context, json)) {
                lastWrittenJson = json
            }
        }
    }

    private fun writeState(context: Context, json: String): Boolean {
        return runCatching {
            AutoSkipAtomicFile.writeText(stateFile(context), json)
            true
        }.getOrDefault(false)
    }
}

data class AutoSkipHealthState(
    val serviceConnected: Boolean = false,
    val lastConnectedAt: Long = 0L,
    val lastHeartbeatAt: Long = 0L,
    val lastEventAt: Long = 0L,
    val lastEventPackageName: String = "",
    val lastRawEventType: Int = 0,
    val lastRawEventPackageName: String = "",
    val lastTitlePackageName: String = "",
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
            put("lastEventPackageName", lastEventPackageName)
            put("lastRawEventType", lastRawEventType)
            put("lastRawEventPackageName", lastRawEventPackageName)
            put("lastTitlePackageName", lastTitlePackageName)
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
                lastEventPackageName = obj.optString("lastEventPackageName"),
                lastRawEventType = obj.optInt("lastRawEventType", 0),
                lastRawEventPackageName = obj.optString("lastRawEventPackageName"),
                lastTitlePackageName = obj.optString("lastTitlePackageName"),
                engineGeneration = obj.optInt("engineGeneration", 0),
                lastError = obj.optString("lastError"),
                lastDisconnectReason = obj.optString("lastDisconnectReason")
            )
        }
    }
}

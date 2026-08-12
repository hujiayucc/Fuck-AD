package com.hujiayucc.hook.autoskip

import android.content.Context
import com.hujiayucc.hook.R
import java.io.File
import java.util.concurrent.TimeUnit
import org.json.JSONObject

object AutoSkipDaemonManager {
    private const val DAEMON_SCRIPT_PATH = "/data/adb/service.d/fkad-daemon"
    private const val LEGACY_DAEMON_SCRIPT_PATH = "/data/adb/service.d/fuckad_autoskip_watchdog.sh"
    private const val LOCAL_SCRIPT_FILE = "fkad-daemon"
    private const val CONFIG_FILE = "autoskip_daemon_config.json"
    private const val LOG_FILE = "autoskip_watchdog.log"
    private const val STATUS_FILE = "autoskip_watchdog_status.json"
    private const val COMMAND_TIMEOUT_SECONDS = 10L
    private const val STARTUP_VERIFICATION_TIMEOUT_MS = 5_000L
    private const val STARTUP_VERIFICATION_POLL_MS = 250L

    internal const val STATUS_STALE_AFTER_MS = 90_000L
    internal const val AUTO_REPAIR_COOLDOWN_MS = 15 * 60_000L
    internal const val CURRENT_STATUS_SCHEMA_VERSION = 4

    fun configFile(context: Context): File {
        return File(context.applicationContext.noBackupFilesDir, CONFIG_FILE)
    }

    fun logFile(context: Context): File {
        return File(context.applicationContext.noBackupFilesDir, LOG_FILE)
    }

    fun statusFile(context: Context): File {
        return File(context.applicationContext.noBackupFilesDir, STATUS_FILE)
    }

    fun writeConfig(
        context: Context,
        preserveExistingEnabled: Boolean = false,
        enabledOverride: Boolean? = null
    ): Boolean {
        return runCatching {
            val appContext = context.applicationContext
            val file = configFile(appContext)
            file.parentFile?.mkdirs()
            val existing = runCatching {
                if (AutoSkipAtomicFile.exists(file)) AutoSkipAtomicFile.readText(file) else null
            }.getOrNull()
            val existingJson = existing?.let { text -> runCatching { JSONObject(text) }.getOrNull() }
            val daemonEnabled = enabledOverride ?: if (preserveExistingEnabled) {
                existingJson?.optBoolean("enabled") ?: AutoSkipSettings.daemonKeepAliveEnabled(appContext)
            } else {
                AutoSkipSettings.daemonKeepAliveEnabled(appContext)
            }
            val jsonText = JSONObject().apply {
                put("enabled", daemonEnabled)
            }.toString()
            if (existing == jsonText) return@runCatching true
            AutoSkipAtomicFile.writeText(file, jsonText)
            true
        }.getOrDefault(false)
    }

    fun readStatus(context: Context): DaemonStatus? {
        return runCatching {
            val file = statusFile(context.applicationContext)
            if (!file.exists()) return null
            val json = JSONObject(file.readText())
            DaemonStatus(
                processName = json.optString("processName"),
                pid = json.optInt("pid"),
                lastCheckAt = json.optLong("lastCheckAt"),
                lastAction = json.optString("lastAction"),
                serviceEnabled = json.optBoolean("serviceEnabled"),
                connected = json.optBoolean("connected"),
                heartbeatAgeSeconds = json.optLong("heartbeatAgeSeconds", -1L),
                recoverCount = json.optInt("recoverCount"),
                lastRecoverAt = json.optLong("lastRecoverAt"),
                attemptId = json.optString("attemptId"),
                attemptOwner = json.optString("attemptOwner"),
                attemptStartedAt = json.optLong("attemptStartedAt"),
                attemptFinishedAt = json.optLong("attemptFinishedAt"),
                beforeServicePid = json.optInt("beforeServicePid"),
                afterServicePid = json.optInt("afterServicePid"),
                beforeBound = json.optBoolean("beforeBound"),
                afterBound = json.optBoolean("afterBound"),
                beforeBinding = json.optBoolean("beforeBinding"),
                afterBinding = json.optBoolean("afterBinding"),
                beforeCrashed = json.optBoolean("beforeCrashed"),
                afterCrashed = json.optBoolean("afterCrashed"),
                beforeConnectedAt = json.optLong("beforeConnectedAt"),
                afterConnectedAt = json.optLong("afterConnectedAt"),
                beforeHeartbeatAt = json.optLong("beforeHeartbeatAt"),
                afterHeartbeatAt = json.optLong("afterHeartbeatAt"),
                settingsLockOwner = json.optString("settingsLockOwner"),
                settingsLockLeaseUntil = json.optLong("settingsLockLeaseUntil"),
                schemaVersion = json.optInt("schemaVersion", json.optInt("version"))
            )
        }.getOrNull()
    }

    fun installOrUpdate(context: Context): DaemonOperationResult {
        if (!writeConfig(context, enabledOverride = true)) {
            return DaemonOperationResult(false, "config write failed")
        }
        val appContext = context.applicationContext
        val localScript = writeLocalScript(appContext)
            ?: return DaemonOperationResult(false, "script write failed")
        val command = listOf(
            "mkdir -p /data/adb/service.d",
            "cp ${shellQuote(localScript.absolutePath)} ${shellQuote("$DAEMON_SCRIPT_PATH.tmp")}",
            "chmod 755 ${shellQuote("$DAEMON_SCRIPT_PATH.tmp")}",
            "if [ -f ${shellQuote(LEGACY_DAEMON_SCRIPT_PATH)} ]; then sh ${shellQuote(LEGACY_DAEMON_SCRIPT_PATH)} stop; rm -f ${shellQuote(LEGACY_DAEMON_SCRIPT_PATH)}; fi",
            "if [ -f ${shellQuote(DAEMON_SCRIPT_PATH)} ]; then sh ${shellQuote(DAEMON_SCRIPT_PATH)} stop; fi",
            "mv -f ${shellQuote("$DAEMON_SCRIPT_PATH.tmp")} ${shellQuote(DAEMON_SCRIPT_PATH)}",
            "sh ${shellQuote(DAEMON_SCRIPT_PATH)} start"
        ).joinToString(" && ")
        val commandStartedAt = System.currentTimeMillis() / 1_000L * 1_000L
        if (!runRootCommand(command)) {
            return DaemonOperationResult(false, "root command failed")
        }
        return if (waitForDaemonStartup(appContext, commandStartedAt)) {
            DaemonOperationResult(true, "installed and verified")
        } else {
            DaemonOperationResult(false, "daemon startup verification failed")
        }
    }

    fun stopAndUninstall(context: Context): DaemonOperationResult {
        writeConfig(context, enabledOverride = false)
        val command = listOf(
            "if [ -f ${shellQuote(DAEMON_SCRIPT_PATH)} ]; then sh ${shellQuote(DAEMON_SCRIPT_PATH)} stop; fi",
            "if [ -f ${shellQuote(LEGACY_DAEMON_SCRIPT_PATH)} ]; then sh ${shellQuote(LEGACY_DAEMON_SCRIPT_PATH)} stop; fi",
            "rm -f ${shellQuote(DAEMON_SCRIPT_PATH)} ${shellQuote(LEGACY_DAEMON_SCRIPT_PATH)}"
        ).joinToString("; ")
        return if (runRootCommand(command)) {
            DaemonOperationResult(true, "uninstalled")
        } else {
            DaemonOperationResult(false, "root command failed")
        }
    }

    private fun writeLocalScript(context: Context): File? {
        return runCatching {
            val file = File(context.applicationContext.noBackupFilesDir, LOCAL_SCRIPT_FILE)
            file.parentFile?.mkdirs()
            context.resources.openRawResource(R.raw.autoskip_watchdog).use { input ->
                AutoSkipAtomicFile.writeFrom(file, input)
            }
            file.setReadable(true, true)
            file.setExecutable(true, true)
            file
        }.getOrNull()
    }

    private fun waitForDaemonStartup(context: Context, startedAt: Long): Boolean {
        val deadline = System.currentTimeMillis() + STARTUP_VERIFICATION_TIMEOUT_MS
        do {
            val status = readStatus(context)
            if (status != null && status.pid > 0 && status.lastCheckAt >= startedAt && daemonProcessRunning(status.pid)) {
                return true
            }
            try {
                Thread.sleep(STARTUP_VERIFICATION_POLL_MS)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return false
            }
        } while (System.currentTimeMillis() < deadline)
        return false
    }

    private fun daemonProcessRunning(pid: Int): Boolean {
        if (pid <= 0) return false
        val command = "test -r /proc/$pid/cmdline && " +
            "tr '\\000' ' ' < /proc/$pid/cmdline | grep -Fq ${shellQuote("$DAEMON_SCRIPT_PATH __daemon_child")}"
        return runRootCommand(command)
    }

    private fun runRootCommand(command: String): Boolean {
        return runCatching {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
            val finished = process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            if (!finished) {
                process.destroy()
                return false
            }
            process.exitValue() == 0
        }.getOrDefault(false)
    }

    private fun shellQuote(value: String): String {
        if (value.all { it.isLetterOrDigit() || it in "_./:@=-" }) return value
        return "'" + value.replace("'", "'\"'\"'") + "'"
    }
}

data class DaemonStatus(
    val processName: String,
    val pid: Int,
    val lastCheckAt: Long,
    val lastAction: String,
    val serviceEnabled: Boolean,
    val connected: Boolean,
    val heartbeatAgeSeconds: Long,
    val recoverCount: Int,
    val lastRecoverAt: Long,
    val attemptId: String = "",
    val attemptOwner: String = "",
    val attemptStartedAt: Long = 0L,
    val attemptFinishedAt: Long = 0L,
    val beforeServicePid: Int = 0,
    val afterServicePid: Int = 0,
    val beforeBound: Boolean = false,
    val afterBound: Boolean = false,
    val beforeBinding: Boolean = false,
    val afterBinding: Boolean = false,
    val beforeCrashed: Boolean = false,
    val afterCrashed: Boolean = false,
    val beforeConnectedAt: Long = 0L,
    val afterConnectedAt: Long = 0L,
    val beforeHeartbeatAt: Long = 0L,
    val afterHeartbeatAt: Long = 0L,
    val settingsLockOwner: String = "",
    val settingsLockLeaseUntil: Long = 0L,
    val schemaVersion: Int = 0
)

data class DaemonOperationResult(
    val success: Boolean,
    val message: String
)

internal fun shouldAutoRepairDaemon(
    daemonEnabled: Boolean,
    statusLastCheckAt: Long?,
    lastRepairAttemptAt: Long,
    now: Long,
    statusSchemaVersion: Int? = null,
    statusStaleAfterMs: Long = AutoSkipDaemonManager.STATUS_STALE_AFTER_MS,
    repairCooldownMs: Long = AutoSkipDaemonManager.AUTO_REPAIR_COOLDOWN_MS,
    requiredStatusSchemaVersion: Int = AutoSkipDaemonManager.CURRENT_STATUS_SCHEMA_VERSION
): Boolean {
    if (!daemonEnabled) return false
    val statusFresh = statusLastCheckAt != null &&
        statusLastCheckAt > 0L &&
        now >= statusLastCheckAt &&
        now - statusLastCheckAt <= statusStaleAfterMs
    val statusSchemaCurrent = statusSchemaVersion != null &&
        statusSchemaVersion >= requiredStatusSchemaVersion
    if (statusFresh && statusSchemaCurrent) return false
    return lastRepairAttemptAt <= 0L || now < lastRepairAttemptAt || now - lastRepairAttemptAt >= repairCooldownMs
}
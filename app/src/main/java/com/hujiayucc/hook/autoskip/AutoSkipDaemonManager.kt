package com.hujiayucc.hook.autoskip

import android.content.ComponentName
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
    private const val DEFAULT_INTERVAL_SECONDS = 20
    private const val DEFAULT_STALE_SECONDS = 45
    private const val DEFAULT_MAX_RECOVER_PER_HOUR = 3
    private const val COMMAND_TIMEOUT_SECONDS = 10L

    fun configFile(context: Context): File {
        return File(context.applicationContext.noBackupFilesDir, CONFIG_FILE)
    }

    fun logFile(context: Context): File {
        return File(context.applicationContext.noBackupFilesDir, LOG_FILE)
    }

    fun writeConfig(context: Context) {
        runCatching {
            val appContext = context.applicationContext
            val file = configFile(appContext)
            file.parentFile?.mkdirs()
            val tempFile = File(file.parentFile, file.name + ".tmp")
            val json = JSONObject().apply {
                put("enabled", AutoSkipSettings.daemonKeepAliveEnabled(appContext))
                put("packageName", appContext.packageName)
                put("serviceComponent", accessibilityServiceComponent(appContext))
                put("healthFile", AutoSkipHealth.stateFile(appContext).absolutePath)
                put("logFile", logFile(appContext).absolutePath)
                put("intervalSeconds", DEFAULT_INTERVAL_SECONDS)
                put("staleSeconds", DEFAULT_STALE_SECONDS)
                put("maxRecoverPerHour", DEFAULT_MAX_RECOVER_PER_HOUR)
                put("reenableWhenUserDisabled", false)
            }
            tempFile.writeText(json.toString())
            if (!tempFile.renameTo(file)) {
                file.writeText(json.toString())
                tempFile.delete()
            }
        }
    }

    fun installOrUpdate(context: Context): DaemonOperationResult {
        writeConfig(context)
        val appContext = context.applicationContext
        val localScript = writeLocalScript(appContext)
            ?: return DaemonOperationResult(false, "script write failed")
        val command = listOf(
            "mkdir -p /data/adb/service.d",
            "if [ -f ${shellQuote(LEGACY_DAEMON_SCRIPT_PATH)} ]; then sh ${shellQuote(LEGACY_DAEMON_SCRIPT_PATH)} stop; rm -f ${shellQuote(LEGACY_DAEMON_SCRIPT_PATH)}; fi",
            "cp ${shellQuote(localScript.absolutePath)} ${shellQuote(DAEMON_SCRIPT_PATH)}",
            "chmod 755 ${shellQuote(DAEMON_SCRIPT_PATH)}",
            "sh ${shellQuote(DAEMON_SCRIPT_PATH)} start"
        ).joinToString(" && ")
        return if (runRootCommand(command)) {
            DaemonOperationResult(true, "installed")
        } else {
            DaemonOperationResult(false, "root command failed")
        }
    }

    fun stopAndUninstall(context: Context): DaemonOperationResult {
        writeConfig(context)
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
                file.outputStream().use { output -> input.copyTo(output) }
            }
            file.setReadable(true, true)
            file.setExecutable(true, true)
            file
        }.getOrNull()
    }

    private fun accessibilityServiceComponent(context: Context): String {
        return ComponentName(context, AutoSkipAccessibilityService::class.java).flattenToString()
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

data class DaemonOperationResult(
    val success: Boolean,
    val message: String
)
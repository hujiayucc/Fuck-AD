package com.hujiayucc.hook.utils

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process as AndroidProcess
import androidx.core.content.ContextCompat
import rikka.shizuku.Shizuku
import java.lang.reflect.Method
import java.util.Locale
import java.util.concurrent.TimeUnit

object PrivilegedPermissionGrantor {
    const val SHIZUKU_PERMISSION_REQUEST_CODE = 1001

    private const val APP_OP_QUERY_ALL_PACKAGES = "android:query_all_packages"
    private const val APP_OP_QUERY_ALL_PACKAGES_LEGACY = "QUERY_ALL_PACKAGES"
    private const val APP_OP_GET_INSTALLED_APPS = "android:get_installed_apps"
    private const val APP_OP_GET_INSTALLED_APPS_LEGACY = "GET_INSTALLED_APPS"
    private const val APP_OP_MIUI_INSTALLED_APPS = "10022"
    private val requiredAppOpsForInstalledApps = listOf(
        APP_OP_QUERY_ALL_PACKAGES
    )
    private val appOpsForInstalledApps = listOf(
        APP_OP_QUERY_ALL_PACKAGES,
        APP_OP_QUERY_ALL_PACKAGES_LEGACY,
        APP_OP_GET_INSTALLED_APPS,
        APP_OP_GET_INSTALLED_APPS_LEGACY,
        APP_OP_MIUI_INSTALLED_APPS
    )
    private const val COMMAND_TIMEOUT_SECONDS = 5L
    @Volatile
    private var miuiInstalledAppsGrantedByShell = false

    enum class GrantResult {
        GRANTED,
        WAITING_FOR_SHIZUKU,
        FAILED
    }

    fun ensureQueryAllPackages(context: Context): GrantResult {
        if (hasQueryAllPackages(context)) return GrantResult.GRANTED

        val shizukuResult = grantByShizuku(context)
        if (shizukuResult == GrantResult.GRANTED || shizukuResult == GrantResult.WAITING_FOR_SHIZUKU) {
            return shizukuResult
        }

        return if (grantByRoot(context)) GrantResult.GRANTED else GrantResult.FAILED
    }

    fun hasQueryAllPackages(context: Context): Boolean {
        return hasQueryAllPackagesPermission(context) && isQueryAllPackagesAppOpAllowed(context)
    }

    fun canQueryAllPackages(context: Context): Boolean {
        return hasQueryAllPackages(context)
    }

    private fun hasQueryAllPackagesPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.QUERY_ALL_PACKAGES
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun isQueryAllPackagesAppOpAllowed(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return true
        val requiredAllowed = requiredAppOpsForInstalledApps.all { operation ->
            queryAppOpMode(context, operation) == AppOpsManager.MODE_ALLOWED
        }
        val miuiAllowed = !isMiuiInstalledAppsAppOpRequired() || miuiInstalledAppsGrantedByShell ||
            queryAppOpMode(context, APP_OP_MIUI_INSTALLED_APPS) == AppOpsManager.MODE_ALLOWED
        return requiredAllowed && miuiAllowed
    }

    private fun queryAppOpMode(context: Context, operation: String): Int? {
        return operation.toIntOrNull()?.let { queryIntAppOpMode(context, it) } ?: queryStringAppOpMode(context, operation)
    }

    private fun queryStringAppOpMode(context: Context, operation: String): Int? {
        return runCatching {
            val appOps = context.getSystemService(AppOpsManager::class.java)
            appOps.unsafeCheckOpNoThrow(
                operation,
                AndroidProcess.myUid(),
                context.packageName
            )
        }.getOrNull()
    }

    private fun queryIntAppOpMode(context: Context, operation: Int): Int? {
        return queryIntAppOpModeByUnsafeCheck(context, operation)
            ?: queryIntAppOpModeByCheck(context, operation)
    }

    private fun queryIntAppOpModeByUnsafeCheck(context: Context, operation: Int): Int? {
        return runCatching {
            val appOps = context.getSystemService(AppOpsManager::class.java)
            AppOpsManager::class.java.getDeclaredMethod(
                "unsafeCheckOpNoThrow",
                Integer.TYPE,
                Integer.TYPE,
                String::class.java
            ).apply { isAccessible = true }
                .invoke(appOps, operation, AndroidProcess.myUid(), context.packageName) as Int
        }.getOrNull()
    }

    private fun queryIntAppOpModeByCheck(context: Context, operation: Int): Int? {
        return runCatching {
            val appOps = context.getSystemService(AppOpsManager::class.java)
            AppOpsManager::class.java.getDeclaredMethod(
                "checkOpNoThrow",
                Integer.TYPE,
                Integer.TYPE,
                String::class.java
            ).apply { isAccessible = true }
                .invoke(appOps, operation, AndroidProcess.myUid(), context.packageName) as Int
        }.getOrNull()
    }

    private fun isMiuiInstalledAppsAppOpRequired(): Boolean {
        val manufacturer = Build.MANUFACTURER.orEmpty().lowercase(Locale.ROOT)
        val brand = Build.BRAND.orEmpty().lowercase(Locale.ROOT)
        val display = Build.DISPLAY.orEmpty().lowercase(Locale.ROOT)
        return manufacturer.contains("xiaomi") || manufacturer.contains("redmi") ||
            manufacturer.contains("poco") || brand.contains("xiaomi") ||
            brand.contains("redmi") || brand.contains("poco") || display.contains("miui") ||
            display.contains("hyperos")
    }

    fun requestShizukuPermissionIfNeeded(): Boolean {
        if (!isShizukuAvailable()) return false
        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) return false
        if (Shizuku.shouldShowRequestPermissionRationale()) return false
        return runCatching {
            Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE)
            true
        }.getOrDefault(false)
    }

    private fun grantByShizuku(context: Context): GrantResult {
        if (!isShizukuAvailable()) return GrantResult.FAILED
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            return GrantResult.FAILED
        }

        return if (runGrantCommands(context, ::runShizukuCommand)) {
            GrantResult.GRANTED
        } else {
            GrantResult.FAILED
        }
    }

    private fun grantByRoot(context: Context): Boolean {
        return runGrantCommands(context) { command ->
            runCatching {
                Runtime.getRuntime().exec(arrayOf("su", "-c", command.joinToString(" "))).waitForSuccess()
            }.getOrDefault(false)
        }
    }

    private fun runGrantCommands(context: Context, runner: (Array<String>) -> Boolean): Boolean {
        val ranAnyCommand = appOpsForInstalledApps.fold(false) { anySucceeded, operation ->
            val operationSucceeded = grantCommandsForOperation(context, operation).any { command -> runner(command) }
            if (operation == APP_OP_MIUI_INSTALLED_APPS && operationSucceeded) {
                miuiInstalledAppsGrantedByShell = true
            }
            anySucceeded || operationSucceeded
        }
        if (ranAnyCommand) runner(arrayOf("appops", "write-settings"))
        return hasQueryAllPackages(context)
    }

    private fun grantCommands(context: Context): List<Array<String>> {
        return appOpsForInstalledApps.flatMap { operation ->
            grantCommandsForOperation(context, operation)
        }
    }

    private fun grantCommandsForOperation(context: Context, operation: String): List<Array<String>> {
        val userId = AndroidProcess.myUid() / 100000
        return listOf(
            arrayOf("appops", "set", "--user", userId.toString(), context.packageName, operation, "allow"),
            arrayOf("/system/bin/appops", "set", "--user", userId.toString(), context.packageName, operation, "allow"),
            arrayOf("cmd", "appops", "set", "--user", userId.toString(), context.packageName, operation, "allow"),
            arrayOf("appops", "set", context.packageName, operation, "allow"),
            arrayOf("/system/bin/appops", "set", context.packageName, operation, "allow"),
            arrayOf("cmd", "appops", "set", context.packageName, operation, "allow"),
            arrayOf("appops", "set", "--uid", context.packageName, operation, "allow"),
            arrayOf("/system/bin/appops", "set", "--uid", context.packageName, operation, "allow"),
            arrayOf("cmd", "appops", "set", "--uid", context.packageName, operation, "allow")
        )
    }

    private fun runShizukuCommand(command: Array<String>): Boolean {
        return runCatching {
            shizukuNewProcessMethod()
                .invoke(null, command, null, null)
                .let { it as Process }
                .waitForSuccess()
        }.getOrDefault(false)
    }

    private fun shizukuNewProcessMethod(): Method {
        return Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java
        ).apply { isAccessible = true }
    }

    private fun Process.waitForSuccess(): Boolean {
        val finished = waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        if (!finished) {
            destroy()
            return false
        }
        return exitValue() == 0
    }

    private fun isShizukuAvailable(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && runCatching { Shizuku.pingBinder() }
            .getOrDefault(false)
    }
}
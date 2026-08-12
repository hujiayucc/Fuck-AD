package com.hujiayucc.hook.utils

import android.Manifest
import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process as AndroidProcess
import android.provider.Settings
import android.text.TextUtils
import androidx.core.content.ContextCompat
import com.hujiayucc.hook.autoskip.AccessibilitySettingsLease
import com.hujiayucc.hook.autoskip.AutoSkipHealth
import rikka.shizuku.Shizuku
import java.lang.reflect.Method
import java.util.Locale

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
    private const val ACCESSIBILITY_CONNECTION_TIMEOUT_MS = 5_000L
    private const val ACCESSIBILITY_CONNECTION_POLL_MS = 100L
    @Volatile
    private var miuiInstalledAppsGrantedByShell = false
    @Volatile
    private var cachedUnsafeCheckOpNoThrowMethod: Method? = null
    @Volatile
    private var cachedCheckOpNoThrowMethod: Method? = null

    enum class GrantResult {
        GRANTED,
        WAITING_FOR_SHIZUKU,
        WAITING_FOR_SETTINGS_LOCK,
        FAILED
    }

    fun ensureAccessibilityServiceEnabled(context: Context, serviceClass: Class<*>): GrantResult {
        val lease = AccessibilitySettingsLease.acquire(context, "client_accessibility_enable")
            ?: return GrantResult.WAITING_FOR_SETTINGS_LOCK
        return try {
            ensureAccessibilityServiceEnabledLocked(context, serviceClass)
        } finally {
            lease.close()
        }
    }

    private fun ensureAccessibilityServiceEnabledLocked(context: Context, serviceClass: Class<*>): GrantResult {
        val componentName = ComponentName(context, serviceClass).flattenToString()
        val currentServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty()
        val enabledFlag = Settings.Secure.getInt(
            context.contentResolver,
            Settings.Secure.ACCESSIBILITY_ENABLED,
            0
        )
        val configured = isAccessibilityServiceEnabled(context, componentName)
        val initialTransaction = buildAccessibilityEnableTransaction(currentServices, componentName, configured)
        val transactionServiceValues = linkedSetOf<String>().apply {
            addAll(initialTransaction.writtenServiceValues)
        }

        val shizukuAttemptStartedAt = System.currentTimeMillis()
        val shizukuResult = runAccessibilityEnableCommands(initialTransaction.commands)
        if (shizukuResult == GrantResult.GRANTED &&
            isAccessibilityServiceEnabled(context, componentName) &&
            waitForAccessibilityServiceConnection(context, shizukuAttemptStartedAt)
        ) {
            return GrantResult.GRANTED
        }

        // Shizuku can fail after a partial write. Re-read under the same shared
        // lease before the Root fallback so a concurrent external update is not
        // overwritten by commands calculated from the original snapshot.
        val rootCurrentServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty()
        val rootConfigured = isAccessibilityServiceEnabled(context, componentName)
        val rootTransaction = buildAccessibilityEnableTransaction(rootCurrentServices, componentName, rootConfigured)
        transactionServiceValues += rootTransaction.writtenServiceValues
        val rootAttemptStartedAt = System.currentTimeMillis()
        val rootSucceeded = runRootCommands(rootTransaction.commands)
        if (rootSucceeded && isAccessibilityServiceEnabled(context, componentName) &&
            waitForAccessibilityServiceConnection(context, rootAttemptStartedAt)
        ) {
            return GrantResult.GRANTED
        }

        rollbackAccessibilitySettings(context, enabledFlag, currentServices, transactionServiceValues)
        return GrantResult.FAILED
    }

    private fun buildAccessibilityEnableTransaction(
        currentServices: String,
        componentName: String,
        configured: Boolean
    ): AccessibilityEnableTransaction {
        val updatedServices = mergeColonList(currentServices, componentName)
        val withoutTarget = removeColonList(currentServices, componentName)
        val commands = if (configured) {
            listOf(
                arrayOf(
                    "settings",
                    "put",
                    "secure",
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                    withoutTarget
                ),
                arrayOf("settings", "put", "secure", Settings.Secure.ACCESSIBILITY_ENABLED, "1"),
                arrayOf(
                    "settings",
                    "put",
                    "secure",
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                    updatedServices
                )
            )
        } else {
            listOf(
                arrayOf("settings", "put", "secure", Settings.Secure.ACCESSIBILITY_ENABLED, "1"),
                arrayOf(
                    "settings",
                    "put",
                    "secure",
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                    updatedServices
                )
            )
        }
        return AccessibilityEnableTransaction(
            commands = commands,
            writtenServiceValues = if (configured) setOf(withoutTarget, updatedServices) else setOf(updatedServices)
        )
    }

    private data class AccessibilityEnableTransaction(
        val commands: List<Array<String>>,
        val writtenServiceValues: Set<String>
    )

    fun isAccessibilityServiceEnabled(context: Context, componentName: String): Boolean {
        if (Settings.Secure.getInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0) != 1) {
            return false
        }
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabledServices.split(':').any { serviceName ->
            accessibilityComponentsEqual(serviceName, componentName)
        }
    }

    fun ensureQueryAllPackages(context: Context): GrantResult {
        if (hasQueryAllPackages(context)) return GrantResult.GRANTED

        val shizukuResult = grantByShizuku(context)
        if (shizukuResult == GrantResult.GRANTED) return shizukuResult

        return if (grantByRoot(context)) GrantResult.GRANTED else GrantResult.FAILED
    }

    fun hasQueryAllPackages(context: Context): Boolean {
        return hasQueryAllPackagesPermission(context) && isQueryAllPackagesAppOpAllowed(context)
    }

    private fun hasQueryAllPackagesPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.QUERY_ALL_PACKAGES
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun isQueryAllPackagesAppOpAllowed(context: Context): Boolean {
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
            unsafeCheckOpNoThrowMethod()
                .invoke(appOps, operation, AndroidProcess.myUid(), context.packageName) as Int
        }.getOrNull()
    }

    private fun queryIntAppOpModeByCheck(context: Context, operation: Int): Int? {
        return runCatching {
            val appOps = context.getSystemService(AppOpsManager::class.java)
            checkOpNoThrowMethod()
                .invoke(appOps, operation, AndroidProcess.myUid(), context.packageName) as Int
        }.getOrNull()
    }

    private fun unsafeCheckOpNoThrowMethod(): Method {
        cachedUnsafeCheckOpNoThrowMethod?.let { return it }
        return synchronized(PrivilegedPermissionGrantor::class.java) {
            cachedUnsafeCheckOpNoThrowMethod ?: AppOpsManager::class.java.getDeclaredMethod(
                "unsafeCheckOpNoThrow",
                Integer.TYPE,
                Integer.TYPE,
                String::class.java
            ).apply { isAccessible = true }
                .also { cachedUnsafeCheckOpNoThrowMethod = it }
        }
    }

    private fun checkOpNoThrowMethod(): Method {
        cachedCheckOpNoThrowMethod?.let { return it }
        return synchronized(PrivilegedPermissionGrantor::class.java) {
            cachedCheckOpNoThrowMethod ?: AppOpsManager::class.java.getDeclaredMethod(
                "checkOpNoThrow",
                Integer.TYPE,
                Integer.TYPE,
                String::class.java
            ).apply { isAccessible = true }
                .also { cachedCheckOpNoThrowMethod = it }
        }
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
                Runtime.getRuntime().exec(arrayOf("su", "-c", command.joinToString(" ")))
                    .waitForSuccess(COMMAND_TIMEOUT_SECONDS)
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

    private fun runAccessibilityEnableCommands(commands: List<Array<String>>): GrantResult {
        if (!isShizukuAvailable()) return GrantResult.FAILED
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) return GrantResult.FAILED
        return if (commands.all(::runShizukuCommand)) GrantResult.GRANTED else GrantResult.FAILED
    }

    private fun runRootCommands(commands: List<Array<String>>): Boolean {
        return commands.all { command ->
            runCatching {
                Runtime.getRuntime().exec(arrayOf("su", "-c", shellCommand(command)))
                    .waitForSuccess(COMMAND_TIMEOUT_SECONDS)
            }.getOrDefault(false)
        }
    }

    private fun waitForAccessibilityServiceConnection(context: Context, attemptStartedAt: Long): Boolean {
        val deadline = System.currentTimeMillis() + ACCESSIBILITY_CONNECTION_TIMEOUT_MS
        while (System.currentTimeMillis() <= deadline) {
            val health = AutoSkipHealth.read(context)
            if (AutoSkipHealth.hasFreshHeartbeat(health, System.currentTimeMillis()) &&
                (health?.lastConnectedAt ?: 0L) >= attemptStartedAt
            ) {
                return true
            }
            try {
                Thread.sleep(ACCESSIBILITY_CONNECTION_POLL_MS)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return false
            }
        }
        val health = AutoSkipHealth.read(context)
        return AutoSkipHealth.hasFreshHeartbeat(health, System.currentTimeMillis()) &&
            (health?.lastConnectedAt ?: 0L) >= attemptStartedAt
    }

    private fun rollbackAccessibilitySettings(
        context: Context,
        previousEnabledValue: Int,
        previousServices: String,
        transactionServiceValues: Set<String>
    ) {
        val appContext = context.applicationContext
        val currentEnabled = runCatching {
            Settings.Secure.getInt(
                appContext.contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED,
                -1
            )
        }.getOrNull()
        val currentServices = runCatching {
            Settings.Secure.getString(
                appContext.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
        }.getOrNull()
        val rollbackCommands = mutableListOf<Array<String>>()
        if (shouldRollbackAccessibilityEnabled(currentEnabled)) {
            rollbackCommands += arrayOf(
                "settings",
                "put",
                "secure",
                Settings.Secure.ACCESSIBILITY_ENABLED,
                previousEnabledValue.toString()
            )
        }
        if (shouldRollbackAccessibilityServices(currentServices, transactionServiceValues)) {
            rollbackCommands += arrayOf(
                "settings",
                "put",
                "secure",
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                previousServices
            )
        }
        if (rollbackCommands.isEmpty()) return
        if (runAccessibilityEnableCommands(rollbackCommands) == GrantResult.GRANTED) return
        runRootCommands(rollbackCommands)
    }

    private fun mergeColonList(current: String, entry: String): String {
        val values = current
            .split(':')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toMutableList()
        if (values.none { accessibilityComponentsEqual(it, entry) }) values += entry
        return values.joinToString(":")
    }

    private fun removeColonList(current: String, entry: String): String {
        return current
            .split(':')
            .map { it.trim() }
            .filter { it.isNotEmpty() && !accessibilityComponentsEqual(it, entry) }
            .joinToString(":")
    }

    private fun shellCommand(command: Array<String>): String {
        return command.joinToString(" ") { argument -> shellQuote(argument) }
    }

    private fun shellQuote(argument: String): String {
        if (argument.all { it.isLetterOrDigit() || it in "_./:@=-" }) return argument
        return "'" + argument.replace("'", "'\"'\"'") + "'"
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
        return ShizukuProcessExecutor.run(command, COMMAND_TIMEOUT_SECONDS)
    }

    private fun isShizukuAvailable(): Boolean {
        return runCatching { Shizuku.pingBinder() }.getOrDefault(false)
    }
}

internal fun accessibilityComponentsEqual(left: String, right: String): Boolean {
    val normalizedLeft = normalizeAccessibilityComponent(left)
    val normalizedRight = normalizeAccessibilityComponent(right)
    return if (normalizedLeft != null && normalizedRight != null) {
        normalizedLeft == normalizedRight
    } else {
        left.trim().equals(right.trim(), ignoreCase = true)
    }
}

internal fun shouldRollbackAccessibilityEnabled(currentEnabled: Int?): Boolean {
    return currentEnabled == 1
}

internal fun shouldRollbackAccessibilityServices(
    currentServices: String?,
    transactionServiceValues: Set<String>
): Boolean {
    return currentServices != null && currentServices in transactionServiceValues
}

private fun normalizeAccessibilityComponent(value: String): String? {
    val trimmed = value.trim()
    val separator = trimmed.indexOf('/')
    if (separator <= 0 || separator >= trimmed.lastIndex) return null
    val packageName = trimmed.substring(0, separator)
    val className = trimmed.substring(separator + 1)
    val fullClassName = when {
        className.startsWith(".") -> packageName + className
        className.startsWith("$packageName.") -> className
        else -> className
    }
    return "${packageName.lowercase(Locale.ROOT)}/${fullClassName.lowercase(Locale.ROOT)}"
}
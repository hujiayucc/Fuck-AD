package com.hujiayucc.hook.utils

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import android.view.View

object AppInfoUtil {
    /** 获取版本名 */
    val Context.appVersionName get() = packageInfo.versionName.orEmpty()

    /** 获取版本号 */
    val Context.appVersionCode get() = packageInfo.longVersionCode

    /** 获取应用名 */
    val Context.appName get() = packageInfo.applicationInfo?.loadLabel(packageManager).toString()

    /** 获取应用图标 */
    val Context.appIcon get() = packageInfo.applicationInfo?.loadIcon(packageManager)

    /** 获取应用列表 */
    private val Context.allApps: List<PackageInfo>
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getInstalledPackages(PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getInstalledPackages(0)
        }

    /** 获取版本名 */
    fun Context.appVersionName(packageName: String) = packageInfo(packageName).versionName.orEmpty()

    /** 获取版本号 */
    fun Context.appVersionCode(packageName: String) = packageInfo(packageName).longVersionCode

    /** 获取应用名 */
    fun Context.appName(packageName: String) =
        packageInfo(packageName).applicationInfo?.loadLabel(packageManager).toString()

    /** 获取应用图标 */
    fun Context.appIcon(packageName: String) =
        packageInfo(packageName).applicationInfo?.loadIcon(packageManager)

    /** 获取用户应用列表 */
    val Context.onlyUserApps: List<PackageInfo>
        get() {
            return allApps.filter { packageInfo ->
                packageInfo.applicationInfo.isSystemApp().not()
            }
        }

    /** 获取系统应用列表 */
    val Context.onlySystemApps: List<PackageInfo>
        get() {
            return allApps.filter { packageInfo ->
                packageInfo.applicationInfo.isSystemApp()
            }
        }

    /** 获取包信息 */
    private val Context.packageInfo get() = packageInfo(packageName)
    private fun Context.packageInfo(packageName: String): PackageInfo {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, 0)
        }
    }

    private fun ApplicationInfo?.isSystemApp(): Boolean {
        return ((this?.flags ?: 0) and ApplicationInfo.FLAG_SYSTEM) != 0
    }

    fun getResourceName(view: View, id: Int): String {
        return try {
            if (id != View.NO_ID) view.resources.getResourceName(id) else "no_id"
        } catch (e: Exception) {
            "unknown"
        }
    }

    fun getActivityFromView(view: View): Activity? {
        var context: Context? = view.context
        var depth = 0
        val maxDepth = 10

        while (context != null && depth < maxDepth) {
            when (context) {
                is Activity -> return context
                is ContextWrapper -> context = context.baseContext
                else -> {
                    Log.d("AppInfoUtil", "Unexpected context type: ${context::class.java.name}")
                    return null
                }
            }
            depth++
        }

        if (context == null) {
            Log.d("AppInfoUtil", "Context became null for view: ${view::class.java.name}")
        } else {
            Log.d("AppInfoUtil", "Max depth reached: $maxDepth for view: ${view::class.java.name}")
        }
        return null
    }
}
package com.hujiayucc.hook.utils

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageInfo
import android.view.View

object AppInfoUtil {
    /** 获取版本名 */
    val Context.appVersionName get() = packageInfo.versionName

    /** 获取版本号 */
    val Context.appVersionCode get() = packageInfo.versionCode

    /** 获取应用名 */
    val Context.appName get() = packageInfo.applicationInfo?.loadLabel(packageManager).toString()

    /** 获取应用图标 */
    val Context.appIcon get() = packageInfo.applicationInfo?.loadIcon(packageManager)

    /** 获取应用列表 */
    private val Context.allApps: MutableList<PackageInfo>
        get() = packageManager.getInstalledPackages(
            0
        )

    /** 获取版本名 */
    fun Context.appVersionName(packageName: String) = packageInfo(packageName).versionName

    /** 获取版本号 */
    fun Context.appVersionCode(packageName: String) = packageInfo(packageName).versionCode

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
                // 使用安全调用操作符处理可能为 null 的情况
                (packageInfo.applicationInfo?.flags
                    ?: 0) and android.content.pm.ApplicationInfo.FLAG_SYSTEM == 0
            }
        }

    /** 获取系统应用列表 */
    val Context.onlySystemApps: List<PackageInfo>
        get() {
            return allApps.filter { packageInfo ->
                (packageInfo.applicationInfo?.flags
                    ?: 0) and android.content.pm.ApplicationInfo.FLAG_SYSTEM != 0
            }
        }

    /** 获取包信息 */
    private val Context.packageInfo get() = packageManager.getPackageInfo(packageName, 0)
    private fun Context.packageInfo(packageName: String) =
        packageManager.getPackageInfo(packageName, 0)

    fun getResourceName(view: View, id: Int): String {
        return try {
            if (id != View.NO_ID) view.resources.getResourceName(id) else "no_id"
        } catch (e: Exception) {
            "unknown"
        }
    }

    fun getActivityFromView(view: View): Activity? {
        var context: Context? = view.context

        // 遍历上下文链寻找 Activity
        while (context != null) {
            when (context) {
                is Activity -> return context
                is ContextWrapper -> context = context.baseContext
                else -> "Unknown: ${context::class.java.name}"
            }
        }

        return null
    }
}
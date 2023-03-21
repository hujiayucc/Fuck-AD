package com.hujiayucc.hook.data

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import android.text.TextUtils
import com.highcapable.yukihookapi.YukiHookAPI
import com.highcapable.yukihookapi.hook.xposed.prefs.data.PrefsData
import com.hujiayucc.hook.utils.Log
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.*

@SuppressLint("SimpleDateFormat", "StaticFieldLeak")
object Data {
    private val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")

    /** 获取项目编译完成的时间戳 (当前本地时间) */
    val buildTime: String = format.format(Date(YukiHookAPI.Status.compiledTimestamp))
    val global: PrefsData<Boolean> = PrefsData("global", true)
    val hookTip: PrefsData<Boolean> = PrefsData("hookTip", true)
    val localeId: PrefsData<Int> = PrefsData("locale", 0)
    val themes: PrefsData<Int> = PrefsData("theme", -25412)
    val background: PrefsData<String> = PrefsData("background", "")

    /**
     * 隐藏或显示启动器图标
     *
     * - 你可能需要 LSPosed 的最新版本以开启高版本系统中隐藏 APP 桌面图标功能
     * @param isShow 是否显示
     */
    fun Context.hideOrShowLauncherIcon(isShow: Boolean) {
        packageManager.setComponentEnabledSetting(
            ComponentName(packageName, "com.hujiayucc.hook.ui.activity.Home"),
            if (isShow) PackageManager.COMPONENT_ENABLED_STATE_DISABLED else PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP
        )
    }

    /**
     * 获取启动器图标状态
     * @return [Boolean] 是否显示
     */
    val Context.isLauncherIconShowing
        get() = packageManager?.getComponentEnabledSetting(
            ComponentName(packageName, "com.hujiayucc.hook.ui.activity.Home")
        ) != PackageManager.COMPONENT_ENABLED_STATE_DISABLED

    /** 检测辅助功能是否开启 */
    fun Context.isAccessibilitySettingsOn(serviceName: String): Boolean {
        var accessibilityEnabled = 0
        // 对应的服务
        val service = "$packageName/$serviceName"
        try {
            accessibilityEnabled = Settings.Secure.getInt(
                contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED
            )
        } catch (e: Settings.SettingNotFoundException) {
            Log.e("Error finding setting, default accessibility to not found: " + e.message)
        }
        val mStringColonSplitter = TextUtils.SimpleStringSplitter(':')
        if (accessibilityEnabled == 1) {
            val settingValue: String = Settings.Secure.getString(
                contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
            mStringColonSplitter.setString(settingValue)
            while (mStringColonSplitter.hasNext()) {
                val accessibilityService = mStringColonSplitter.next()
                if (accessibilityService.equals(service,true)) {
                    return true
                }
            }
        }
        return false
    }

    /** 判断是否具有ROOT权限 */
    fun checkRoot(): Boolean {
        val process = Runtime.getRuntime().exec("su\n")
        try {
            val os = DataOutputStream(process?.outputStream)
            os.writeBytes("cat /system/build.prop\n")
            os.writeBytes("exit\n")
            os.flush()
            process?.waitFor()
            val result = getStringFromIO(process?.inputStream)
            if (result == null) return false
            else return result.contains("ro.build.id")
        } catch (e : Exception) {
            return false
        }
    }

    private fun getStringFromIO(inputStream: InputStream?): String? {
        var br: BufferedReader? = null
        var result: String? = null
        try {
            var temp: String?
            val sb = StringBuilder()
            br = BufferedReader(InputStreamReader(inputStream))
            while (br.readLine().also { temp = it } != null) {
                sb.append(temp)
            }
            result = sb.toString()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            if (br != null) try {
                br.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return result
    }

    private fun RunAsRoot(cmd: ArrayList<String>) {
        val process = Runtime.getRuntime().exec("su\n")
        val os = DataOutputStream(process.outputStream)
        for (tmpCmd in cmd) {
            os.writeBytes("$tmpCmd\n")
        }
        os.writeBytes("exit\n")
        os.flush()
    }

    fun openService() {
        if (checkRoot()) {
            val cmd = ArrayList<String>()
            cmd.add("settings put secure enabled_accessibility_services com.hujiayucc.hook/com.hujiayucc.hook.service.SkipService")
            cmd.add("settings put secure accessibility_enabled 1")
            RunAsRoot(cmd)
        }
    }
}
@file:Suppress("DEPRECATION")

package com.hujiayucc.hook.data

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import android.text.TextUtils
import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import com.highcapable.yukihookapi.YukiHookAPI
import com.highcapable.yukihookapi.hook.xposed.prefs.data.PrefsData
import com.hujiayucc.hook.utils.Log
import java.io.*
import java.text.SimpleDateFormat
import java.util.*

@SuppressLint("SimpleDateFormat", "StaticFieldLeak")
object Data {
    private val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
    private var process: Process? = null
    private var os: DataOutputStream? = null
    private var isRoot: Boolean = false
    private var result: BufferedReader? = null

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
    @Synchronized
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
        if (isRoot) return true
        try {
            if (process == null) {
                process = Runtime.getRuntime().exec("su\n")
                os = DataOutputStream(process?.outputStream)
            }
            os?.writeBytes("cat /system/build.prop\n")
            os?.flush()
            result = BufferedReader(InputStreamReader(process?.inputStream))
            var temp: String
            while (result!!.readLine().also { temp = it }.isNotEmpty()) {
                if (temp.contains("ro.build.id")) {
                    isRoot = true
                    return true
                }
            }
            return false
        } catch (e : Exception) {
            process = null
            os = null
            e.printStackTrace()
            return false
        }
    }

    private fun RunAsRoot(cmd: ArrayList<String>): Boolean {
        return try {
            for (tmpCmd in cmd) {
                os?.writeBytes("$tmpCmd\n")
            }
            os?.flush()
            true
        } catch (e : Exception) {
            isRoot = false
            process = null
            e.printStackTrace()
            false
        }
    }

    fun Context.openService() {
        if (checkRoot()) {
            val cmd = ArrayList<String>()
            cmd.add("pm grant com.hujiayucc.hook android.permission.WRITE_SECURE_SETTINGS")
            if (RunAsRoot(cmd)) {
                // 防止关闭其他正在运行的无障碍服务
                val list = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).trim()
                Settings.Secure.putString(contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,"${list}:com.hujiayucc.hook/com.hujiayucc.hook.service.SkipService")
                Settings.Secure.putString(contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, "1")
            }
        }
    }

    fun Context.closeService() {
        if (checkRoot()) {
            val cmd = ArrayList<String>()
            cmd.add("pm grant com.hujiayucc.hook android.permission.WRITE_SECURE_SETTINGS")
            if (RunAsRoot(cmd)) {
                // 防止关闭其他正在运行的无障碍服务
                val list = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                    .replace("com.hujiayucc.hook/com.hujiayucc.hook.service.SkipService:","")
                    .replace(":com.hujiayucc.hook/com.hujiayucc.hook.service.SkipService","")
                    .replace("com.hujiayucc.hook/com.hujiayucc.hook.service.SkipService","").trim()
                if (list.isNotEmpty()) {
                    Settings.Secure.putString(contentResolver,
                        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,list)
                    Settings.Secure.putString(contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, "1")
                } else {
                    cmd.clear()
                    cmd.add("settings delete secure enabled_accessibility_services")
                    RunAsRoot(cmd)
                }
            }
        }
    }

    fun Context.updateConfig(map: Map<String, Any?>) {
        try {
            val jsonObject = JSONObject(map)
            val config = File(filesDir, "config.json")
            if (!config.exists()) config.createNewFile()
            val outputStream = FileOutputStream(config)
            outputStream.write(jsonObject.toJSONString().toByteArray())
            outputStream.flush()
            outputStream.close()
        } catch (e : Exception) {
            e.printStackTrace()
        }
    }

    fun Context.getConfig(key: String): Any? {
        return try {
            val config = File(filesDir, "config.json")
            val inputStream = config.inputStream()
            val byte = ByteArray(config.length().toInt())
            inputStream.read(byte)
            inputStream.close()
            val json = JSON.parseObject(String(byte))
            json[key]
        } catch (e : Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     *
     * @param mContext
     * @param serviceName
     * 是包名+服务的类名（例如：com.example.testbackstage.TestService）
     * @return true代表正在运行，false代表服务没有正在运行
     */
    fun isServiceWork(mContext: Context, serviceName: String): Boolean {
        var isWork = false
        val manager = mContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val list: List<ActivityManager.RunningServiceInfo> = manager.getRunningServices(40)
        if (list.isEmpty()) {
            return false
        }
        for (i in list.indices) {
            val name: String = list[i].service.className
            if (name == serviceName) {
                isWork = true
                break
            }
        }
        return isWork
    }
}
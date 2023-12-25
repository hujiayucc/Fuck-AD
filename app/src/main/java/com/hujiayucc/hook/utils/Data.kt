package com.hujiayucc.hook.utils

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.provider.Settings
import android.text.Spannable
import android.text.SpannableString
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import com.highcapable.yukihookapi.YukiHookAPI
import com.highcapable.yukihookapi.hook.xposed.prefs.data.PrefsData
import com.hujiayucc.hook.BuildConfig.SERVICE_NAME
import com.hujiayucc.hook.service.SkipService
import org.json.JSONException
import org.json.JSONObject
import java.io.*
import java.text.SimpleDateFormat
import java.util.*


@SuppressLint("SimpleDateFormat", "StaticFieldLeak")
object Data {
    private val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
    private var process: Process? = null
    private var os: DataOutputStream? = null

    const val QQ_GROUP = "mqqopensdkapi://bizAgent/qm/qr?url=https%3A%2F%2Fqm.qq.com%2Fcgi-bin%2Fqm%2Fqr%3Fk%3DrrPSIlmQfYaZAlZuYH058gxUzEEKY00y%26jump_from%3D%26auth%3D%26app_name%3D%26authSig%3D2YtvxFdMkwUaQfxU%2FSjV5zDBQMTptBWbBaFeivt3FQXrdorfW9iq4fRDljE3V3At%26source_id%3D3_40001"
    const val action = "com.hujiayucc.hook.service.StartService"
    /** 获取项目编译完成的时间戳 (当前本地时间) */
    val buildTime: String = format.format(Date(YukiHookAPI.Status.compiledTimestamp))
    val global: PrefsData<Boolean> = PrefsData("global", true)
    val hookTip: PrefsData<Boolean> = PrefsData("hookTip", true)
    val localeId: PrefsData<Int> = PrefsData("locale", 0)
    val background: PrefsData<String> = PrefsData("background", "")
    var skipCount = 0

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
        return try {
            process = Runtime.getRuntime().exec("su")
            os = DataOutputStream(process?.outputStream)
            os?.writeBytes("id\n")
            os?.flush()
            BufferedReader(InputStreamReader(process?.inputStream)).readLine().contains("uid=0")
        } catch (e : Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun runAsRoot(cmd: ArrayList<String>): Boolean {
        os?.writeBytes("clear\n")
        os?.flush()
        return try {
            for (tmpCmd in cmd) {
                os?.writeBytes("$tmpCmd\n")
            }
            os?.flush()
            Thread.sleep(200)
            true
        } catch (e : Exception) {
            process = null
            e.printStackTrace()
            false
        }
    }

    private fun openService() {
        if (checkRoot()) {
            val cmd = ArrayList<String>()
            cmd.add("settings put secure enabled_accessibility_services com.hujiayucc.hook/$SERVICE_NAME")
            cmd.add("settings put secure accessibility_enabled 1")
            runAsRoot(cmd)
        }
    }

    private fun closeService() {
        if (checkRoot()) {
            val cmd = ArrayList<String>()
            cmd.add("settings delete secure enabled_accessibility_services")
            runAsRoot(cmd)
        }
    }

    fun Context.runService() {
        val intent = Intent(applicationContext, SkipService::class.java)
        applicationContext.startService(intent)
        openService()
    }

    fun Context.stopService() {
        val intent = Intent(applicationContext, SkipService::class.java)
        applicationContext.stopService(intent)
        closeService()
    }

    fun Context.updateConfig(map: Map<String, Any?>) {
        try {
            val jsonObject = JSONObject(map)
            val config = File(filesDir, "config.json")
            if (!config.exists()) config.createNewFile()
            val outputStream = FileOutputStream(config)
            outputStream.write(jsonObject.toString().toByteArray(Charsets.UTF_8))
            outputStream.flush()
            outputStream.close()
        } catch (e : Exception) {
            e.printStackTrace()
        }
    }

    fun Context.getConfig(): JSONObject {
        return try {
            val config = File(filesDir, "config.json")
            val inputStream = config.inputStream()
            val byte = ByteArray(config.length().toInt())
            inputStream.read(byte)
            inputStream.close()
            JSONObject(String(byte))
        } catch (e : IOException) {
            JSONObject()
        } catch (e : JSONException) {
            JSONObject()
        }
    }

    /**
     * 获取配置中的 [Boolean]
     * @return 键值存在返回键值，不存在返回 true
     */
    fun JSONObject.getBoolean(key: String, value: Boolean = true): Boolean {
        return try {
            getBoolean(key)
        } catch (e : JSONException) {
            value
        }
    }

    /**
     * 获取配置中的 [Int]
     * @return 键值存在返回键值，不存在返回 0
     */
    fun JSONObject.getInt(key: String, value: Int = 0): Int {
        return try {
            getInt(key)
        } catch (e : JSONException) {
            value
        }
    }

    /** 设置文本颜色 */
    fun String.setSpan(color: Int): SpannableString {
        val spannable = SpannableString(this)
        val colors = if (color != 0) color else Color.BLACK
        spannable.setSpan(
            ForegroundColorSpan(colors),
            0,
            length,
            Spannable.SPAN_EXCLUSIVE_INCLUSIVE
        )
        return spannable
    }

    /** 设置文本颜色 */
    fun CharSequence.setSpan(color: Int): SpannableString {
        val spannable = SpannableString(this)
        val colors = if (color != 0) color else Color.BLACK
        spannable.setSpan(
            ForegroundColorSpan(colors),
            0,
            length,
            Spannable.SPAN_EXCLUSIVE_INCLUSIVE
        )
        return spannable
    }

    val Context.id: String @SuppressLint("HardwareIds")
    get() = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)

    val Context.deviceInfo: String @SuppressLint("HardwareIds") get() = JSONObject()
        .put("phone","${Build.BRAND} ${Build.MODEL}")
        .put("android", Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID))
        .toString()
}
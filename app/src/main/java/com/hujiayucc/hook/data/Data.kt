package com.hujiayucc.hook.data

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.highcapable.yukihookapi.YukiHookAPI
import com.highcapable.yukihookapi.hook.xposed.prefs.data.PrefsData
import java.text.SimpleDateFormat
import java.util.*

@SuppressLint("SimpleDateFormat", "StaticFieldLeak")
object Data {
    private val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")

    /** 获取项目编译完成的时间戳 (当前本地时间) */
    val buildTime: String = format.format(Date(YukiHookAPI.Status.compiledTimestamp))
    val global: PrefsData<Boolean> = PrefsData("global", false)
    val hookTip: PrefsData<Boolean> = PrefsData("hookTip", false)
    val localeId: PrefsData<Int> = PrefsData("locale", 0)

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
    val Context.isLauncherIconShowing get() = packageManager?.getComponentEnabledSetting(
            ComponentName(packageName, "com.hujiayucc.hook.ui.activity.Home")
        ) != PackageManager.COMPONENT_ENABLED_STATE_DISABLED
}
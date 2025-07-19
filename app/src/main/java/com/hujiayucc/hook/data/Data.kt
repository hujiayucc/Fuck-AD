package com.hujiayucc.hook.data

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings
import com.highcapable.yukihookapi.YukiHookAPI
import com.highcapable.yukihookapi.hook.factory.prefs
import java.text.SimpleDateFormat
import java.util.Date

object Data {
    /** 获取项目编译完成的时间戳 (当前本地时间) */
    @SuppressLint("SimpleDateFormat")
    val buildTime: String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
        .format(Date(YukiHookAPI.Status.compiledTimestamp))
    val Context.prefsData get() = prefs("config")

    val Context.id: String
        @SuppressLint("HardwareIds")
        get() = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)

    val name: String
        @SuppressLint("HardwareIds") get() =
            if (Build.MODEL.startsWith(Build.MANUFACTURER)) capitalize(Build.MODEL) else capitalize(
                Build.MANUFACTURER
            ) + " " + Build.MODEL

    private fun capitalize(s: String): String {
        if (s.isEmpty()) return ""
        val first = s[0]
        return if (Character.isUpperCase(first)) s else first.uppercaseChar()
            .toString() + s.substring(1)
    }
}
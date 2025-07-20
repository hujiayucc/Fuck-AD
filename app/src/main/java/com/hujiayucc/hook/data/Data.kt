package com.hujiayucc.hook.data

import android.annotation.SuppressLint
import android.content.Context
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
}
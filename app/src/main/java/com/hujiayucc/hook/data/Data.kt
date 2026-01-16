package com.hujiayucc.hook.data

import android.content.Context
import com.highcapable.yukihookapi.hook.factory.prefs
import com.highcapable.yukihookapi.hook.xposed.prefs.YukiHookPrefsBridge
import java.text.SimpleDateFormat
import java.util.Date

object Data {
    val Context.prefsBridge: YukiHookPrefsBridge get() {
        val prefsBridge = prefs()
        val isUsingNative = prefsBridge.getBoolean("usingNative", true)
        return if (!isUsingNative) prefsBridge
        else prefsBridge.native()
    }

    val proxyMap = mapOf(
        "info.muge.appshare" to "info.muge.appshare.view.app.add.AppAddActivity"
    )

    fun String.formatTime(pattern: String = "yyyy-MM-dd"): Date {
        val forMat = SimpleDateFormat(pattern)
        return forMat.parse(this)!!
    }
}
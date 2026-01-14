package com.hujiayucc.hook.data

import android.content.Context
import com.highcapable.yukihookapi.hook.factory.prefs
import com.highcapable.yukihookapi.hook.xposed.prefs.YukiHookPrefsBridge

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
}
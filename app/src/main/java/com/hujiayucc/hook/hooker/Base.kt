package com.hujiayucc.hook.hooker

import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.log.YLog
import com.hujiayucc.hook.annotation.Run
import com.hujiayucc.hook.utils.AppInfoUtil.appVersionName

abstract class Base : YukiBaseHooker() {
    override fun onHook() {
        val versionList = this::class.java.annotations.filterIsInstance<Run>().first().versions
        val appName = this::class.java.annotations.filterIsInstance<Run>().first().appName
        YLog.debug("Hook Start: $appName")
        if (versionList.isNotEmpty() && !versionList.contains(appContext?.appVersionName)) {
            YLog.error("Hook Failed: $appName")
            YLog.error("Current Version: ${appContext?.appVersionName}")
            YLog.error("Not Support Version: ${versionList.contentToString()}")
        } else onStart()
    }

    abstract fun onStart()
}
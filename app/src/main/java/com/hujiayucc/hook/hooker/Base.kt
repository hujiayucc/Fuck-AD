package com.hujiayucc.hook.hooker

import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.log.YLog
import com.hujiayucc.hook.annotation.Run
import com.hujiayucc.hook.utils.AppInfoUtil.appVersionName

abstract class Base : YukiBaseHooker() {
    override fun onHook() {
        val versions = this::class.java.annotations.filterIsInstance<Run>().first().versions
        val appName = this::class.java.annotations.filterIsInstance<Run>().first().appName
        val action = this::class.java.annotations.filterIsInstance<Run>().first().action
        YLog.debug("Hook Start: $appName")
        if (versions.isNotEmpty() && !versions.contains(appContext?.appVersionName)) {
            YLog.error("Hook Failed: $appName")
            YLog.error("Current Version: ${appContext?.appVersionName}")
            YLog.error("Not Support Version: ${versions.contentToString()}")
        } else {
            YLog.debug("$appName => $action")
            onStart()
        }
    }

    abstract fun onStart()
}
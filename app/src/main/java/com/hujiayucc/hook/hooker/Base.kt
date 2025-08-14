package com.hujiayucc.hook.hooker

import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.log.YLog
import com.hujiayucc.hook.annotation.Run
import com.hujiayucc.hook.annotation.RunJiaGu
import com.hujiayucc.hook.utils.AppInfoUtil.appVersionName

abstract class Base : YukiBaseHooker() {
    protected lateinit var versions: Array<String>
    protected lateinit var appName: String
    protected lateinit var action: String
    protected val versionName get() = systemContext.appVersionName(packageName)

    override fun onHook() {
        try {
            versions = this::class.java.annotations.filterIsInstance<Run>().first().versions
            appName = this::class.java.annotations.filterIsInstance<Run>().first().appName
            action = this::class.java.annotations.filterIsInstance<Run>().first().action
        } catch (e: NoSuchElementException) {
            versions = this::class.java.annotations.filterIsInstance<RunJiaGu>().first().versions
            appName = this::class.java.annotations.filterIsInstance<RunJiaGu>().first().appName
            action = this::class.java.annotations.filterIsInstance<RunJiaGu>().first().action
        }
        if (versions.isNotEmpty() && !versions.contains(versionName)) {
            error("Hook Failed: $appName")
            error("Current Version: $versionName")
            error("Support Version: ${versions.contentToString()}")
        } else {
            debug("$appName => $action")
            onStart()
        }
    }

    abstract fun onStart()
    fun debug(msg: String) {
        YLog.debug(msg)
    }

    fun error(msg: String) {
        YLog.error("Hook: $appName => $msg")
    }
}
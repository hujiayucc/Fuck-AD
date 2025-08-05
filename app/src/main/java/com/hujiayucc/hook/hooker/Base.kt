package com.hujiayucc.hook.hooker

import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.log.YLog
import com.hujiayucc.hook.annotation.Run
import com.hujiayucc.hook.utils.AppInfoUtil.appVersionName

abstract class Base : YukiBaseHooker() {
    lateinit var versions: Array<String>
    lateinit var appName: String
    lateinit var action: String
    override fun onHook() {
        runCatching {
            versions = this::class.java.annotations.filterIsInstance<Run>().first().versions
            appName = this::class.java.annotations.filterIsInstance<Run>().first().appName
            action = this::class.java.annotations.filterIsInstance<Run>().first().action
            if (versions.isNotEmpty() && !versions.contains(versionName)) {
                error("Hook Failed: $appName")
                error("Current Version: $versionName")
                error("Support Version: ${versions.contentToString()}")
            } else {
                debug("$appName => $action")
                onStart()
            }
        }
    }

    protected val versionName get() = systemContext.appVersionName(packageName)

    abstract fun onStart()
    fun debug(msg: String) {
        YLog.debug("Hook: $appName => $msg")
    }

    fun error(msg: String) {
        YLog.error("Hook: $appName => $msg")
    }
}
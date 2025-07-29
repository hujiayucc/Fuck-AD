package com.hujiayucc.hook.hooker

import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.log.YLog
import com.hujiayucc.hook.annotation.RunJiaGu
import com.hujiayucc.hook.utils.AppInfoUtil.appVersionName

abstract class BaseJiaGu : YukiBaseHooker() {
    override fun onHook() {
        val versions = this::class.java.annotations.filterIsInstance<RunJiaGu>().first().versions
        val appName = this::class.java.annotations.filterIsInstance<RunJiaGu>().first().appName
        val action = this::class.java.annotations.filterIsInstance<RunJiaGu>().first().action
        if (versions.isNotEmpty() && !versions.contains(versionName)) {
            YLog.error("Hook Failed: $appName")
            YLog.error("Current Version: $versionName")
            YLog.error("Support Version: ${versions.contentToString()}")
        } else {
            YLog.debug("脱壳: $appName => $action")
            onStart()
        }
    }

    protected val versionName get() = systemContext.appVersionName(packageName)

    abstract fun onStart()
}
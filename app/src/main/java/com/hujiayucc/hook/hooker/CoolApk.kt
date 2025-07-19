package com.hujiayucc.hook.hooker

import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.log.YLog

object CoolApk : YukiBaseHooker() {
    override fun onHook() {
        YLog.debug("酷安 => 开始Hook")
    }
}
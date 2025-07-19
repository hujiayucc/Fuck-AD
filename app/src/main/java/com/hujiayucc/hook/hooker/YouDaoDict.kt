package com.hujiayucc.hook.hooker

import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.factory.method
import com.highcapable.yukihookapi.hook.log.YLog

object YouDaoDict : YukiBaseHooker() {
    override fun onHook() {
        YLog.debug("网易有道词典 => 开始Hook")
        "com.youdao.community.extension.ExtensionsKt".toClassOrNull()
            ?.method { name = "H" }
            ?.hook {
                after {
                    YLog.debug("网易有道词典 => 自动跳过开屏广告")
                    val view = args[0] as android.view.View
                    view.performClick()
                }
            }
    }
}
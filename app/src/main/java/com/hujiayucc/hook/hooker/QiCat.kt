package com.hujiayucc.hook.hooker

import android.annotation.SuppressLint
import android.view.View
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.factory.method
import com.highcapable.yukihookapi.hook.log.YLog
import de.robv.android.xposed.XposedHelpers

object QiCat : YukiBaseHooker() {
    @SuppressLint("ResourceType")
    override fun onHook() {
        YLog.debug("七猫免费小说 => 开始Hook")
        "com.qimao.qmad.qmsdk.splash.SplashAdFragmentNew".toClassOrNull()
            ?.method { name = "r0" }
            ?.hook {
                after {
                    YLog.debug("七猫免费小说 => 自动跳过开屏广告")
                    val view = XposedHelpers.getObjectField(instance, "s") as View
                    view.performClick()
                }
            }
    }
}
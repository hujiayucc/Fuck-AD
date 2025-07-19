package com.hujiayucc.hook.hooker

import android.os.Handler
import android.os.Looper
import android.view.View
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.factory.method
import com.highcapable.yukihookapi.hook.log.YLog
import de.robv.android.xposed.XposedHelpers

object Bilibili : YukiBaseHooker() {
    override fun onHook() {
        YLog.debug("哔哩哔哩 => 开始 Hook")
        "tv.danmaku.bili.ui.splash.ad.page.FullImageSplash".toClassOrNull()
            ?.method { name = "ze" }
            ?.hook {
                after {
                    YLog.debug("哔哩哔哩 => 自动跳过开屏广告")
                    val view = XposedHelpers.getObjectField(instance, "v") as View
                    Handler(Looper.getMainLooper()).postDelayed({
                        view.performClick()
                    }, 100)
                }
            }
    }
}
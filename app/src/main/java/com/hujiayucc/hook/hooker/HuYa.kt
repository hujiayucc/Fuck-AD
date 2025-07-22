package com.hujiayucc.hook.hooker

import android.annotation.SuppressLint
import android.view.View
import com.highcapable.yukihookapi.hook.factory.method
import com.highcapable.yukihookapi.hook.log.YLog
import com.hujiayucc.hook.annotation.Run

@Run("虎牙直播", "com.duowan.kiwi")
object HuYa : Base() {
    @SuppressLint("ResourceType")
    override fun onStart() {
        "com.duowan.kiwi.adsplash.view.AdSplashFragment".toClassOrNull()
            ?.method { name = "findViews" }
            ?.hook {
                after {
                    runCatching {
                        YLog.debug("虎牙直播 => 自动跳过开屏广告")
                        val view = (args[0] as View).findViewById<View>(0x7f092472)
                        view.performClick()
                    }
                }
            }
    }
}
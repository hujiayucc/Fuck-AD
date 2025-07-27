package com.hujiayucc.hook.hooker

import android.annotation.SuppressLint
import android.view.View
import com.highcapable.yukihookapi.hook.factory.method
import com.hujiayucc.hook.annotation.Run

@Run(
    "虎牙直播",
    "com.duowan.kiwi",
    "开屏广告",
    [
        "12.7.14"
    ]
)
object HuYa : Base() {
    @SuppressLint("ResourceType")
    override fun onStart() {
        if (versionName == "12.7.14")
        "com.duowan.kiwi.adsplash.view.AdSplashFragment".toClassOrNull()
            ?.method { name = "findViews" }
            ?.hook {
                after {
                    runCatching {
                        val view = (args[0] as View).findViewById<View>(0x7f0923c9)
                        view.performClick()
                    }
                }
            }
    }
}
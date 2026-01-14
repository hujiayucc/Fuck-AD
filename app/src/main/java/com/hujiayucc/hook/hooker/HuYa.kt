package com.hujiayucc.hook.hooker

import android.annotation.SuppressLint
import android.view.View
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.hujiayucc.hook.annotation.Run

@Run(
    appName = "虎牙直播",
    packageName = "com.duowan.kiwi",
    action = "开屏广告",
    versions = [
        "12.7.14"
    ]
)
object HuYa : Base() {
    @SuppressLint("ResourceType")
    override fun onStart() {
        if (versionName == "12.7.14")
            "com.duowan.kiwi.adsplash.view.AdSplashFragment".toClassOrNull()
                ?.resolve()?.firstMethod { name = "findViews" }
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
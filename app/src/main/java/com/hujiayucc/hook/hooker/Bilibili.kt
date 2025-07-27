package com.hujiayucc.hook.hooker

import android.os.Handler
import android.os.Looper
import android.view.View
import com.highcapable.yukihookapi.hook.factory.method
import com.hujiayucc.hook.annotation.Run
import de.robv.android.xposed.XposedHelpers

@Run(
    "哔哩哔哩",
    "tv.danmaku.bili",
    "开屏广告",
    [
        "8.54.0"
    ]
)
object Bilibili : Base() {
    override fun onStart() {
        if (versionName == "8.54.0")
            "tv.danmaku.bili.ui.splash.ad.page.FullImageSplash".toClassOrNull()
                ?.method { name = "y6" }
                ?.hook {
                    after {
                        val view = XposedHelpers.getObjectField(instance, "v") as View
                        Handler(Looper.getMainLooper()).postDelayed({
                            view.performClick()
                        }, 100)
                    }
                }
    }
}
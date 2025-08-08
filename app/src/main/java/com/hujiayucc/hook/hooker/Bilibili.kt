package com.hujiayucc.hook.hooker

import android.os.Handler
import android.os.Looper
import android.view.View
import com.highcapable.yukihookapi.hook.factory.method
import com.hujiayucc.hook.annotation.Run
import de.robv.android.xposed.XposedHelpers

@Run(
    appName = "哔哩哔哩",
    packageName = "tv.danmaku.bili",
    action = "开屏广告",
    versions = [
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
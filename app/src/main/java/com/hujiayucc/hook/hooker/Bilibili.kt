package com.hujiayucc.hook.hooker

import android.os.Handler
import android.os.Looper
import android.view.View
import com.highcapable.yukihookapi.hook.factory.method
import com.highcapable.yukihookapi.hook.log.YLog
import com.hujiayucc.hook.annotation.Run
import de.robv.android.xposed.XposedHelpers

@Run("哔哩哔哩", "tv.danmaku.bili", "开屏广告")
object Bilibili : Base() {
    override fun onStart() {
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
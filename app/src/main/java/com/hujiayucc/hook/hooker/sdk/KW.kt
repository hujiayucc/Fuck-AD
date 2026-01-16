package com.hujiayucc.hook.hooker.sdk

import android.os.Handler
import android.os.Looper
import android.view.View
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker

/** 快手 */
object KW : YukiBaseHooker() {
    override fun onHook() {
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

        "com.kwad.components.ad.splashscreen.widget.CircleSkipView".toClassOrNull()
            ?.resolve()?.method()?.build()?.forEach { method ->
                method.hook {
                    after {
                        val handler = Handler(Looper.getMainLooper())
                        handler.postDelayed({
                            val view = instanceOrNull as View?
                            view?.performClick()
                        }, 200)
                    }
                }
            }
    }
}
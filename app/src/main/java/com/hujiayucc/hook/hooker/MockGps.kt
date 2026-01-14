package com.hujiayucc.hook.hooker

import android.os.Handler
import android.os.Looper
import android.view.View
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.hujiayucc.hook.annotation.Run

@Run(
    appName = "MockGps",
    packageName = "com.huolala.mockgps",
    action = "开屏广告, 底部广告",
    versions = [
        "2.6.1"
    ]
)
object MockGps : Base() {
    override fun onStart() {
        val handler = Handler(Looper.getMainLooper())
        "com.kwad.components.ad.splashscreen.widget.CircleSkipView".toClassOrNull()
            ?.resolve()?.method()?.build()?.forEach { method ->
                method.hook {
                    after {
                        handler.postDelayed({
                            val view = instanceOrNull as View?
                            view?.performClick()
                        }, 200)
                    }
                }
            }

        "androidx.appcompat.widget.AppCompatImageView".toClassOrNull()
            ?.resolve()?.firstMethod { name = "setImageDrawable" }
            ?.hook {
                after {
                    val view = instance as View
                    if (view.id == 0x7f080254) {
                        handler.postDelayed({
                            view.performClick()
                        }, 200)
                    }
                }
            }
    }
}
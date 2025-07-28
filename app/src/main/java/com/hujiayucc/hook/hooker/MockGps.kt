package com.hujiayucc.hook.hooker

import android.os.Handler
import android.os.Looper
import android.view.View
import com.highcapable.yukihookapi.hook.factory.allMethods
import com.highcapable.yukihookapi.hook.factory.method
import com.hujiayucc.hook.annotation.Run

@Run(
    "MockGps",
    "com.huolala.mockgps",
    "开屏广告, 底部广告",
    [
        "2.6.1"
    ]
)
object MockGps : Base() {
    override fun onStart() {
        val handler = Handler(Looper.getMainLooper())
        "com.kwad.components.ad.splashscreen.widget.CircleSkipView".toClassOrNull()
            ?.allMethods { _, method ->
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
            ?.method { name = "setImageDrawable" }
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
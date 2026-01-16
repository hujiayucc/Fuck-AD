package com.hujiayucc.hook.hooker.app

import android.os.Handler
import android.os.Looper
import android.view.View
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.hujiayucc.hook.annotation.Run
import com.hujiayucc.hook.hooker.util.Base

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
        loadSdk(kw = true)
        "androidx.appcompat.widget.AppCompatImageView".toClassOrNull()
            ?.resolve()?.firstMethod { name = "setImageDrawable" }
            ?.hook {
                after {
                    val handler = Handler(Looper.getMainLooper())
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
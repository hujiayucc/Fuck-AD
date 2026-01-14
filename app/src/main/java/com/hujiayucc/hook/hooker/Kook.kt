package com.hujiayucc.hook.hooker

import android.view.View
import android.widget.LinearLayout
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.hujiayucc.hook.annotation.RunJiaGu

@RunJiaGu(
    appName = "KOOK",
    packageName = "cn.kaiheila",
    action = "开屏广告",
    versions = [
        "1.75.0"
    ]
)
object Kook : Base() {
    override fun onStart() {
        LinearLayout::class.resolve().firstMethod { name = "onDraw" }
            .hook {
                after {
                    val layout = instance as LinearLayout
                    if (layout.id == 0x7f0a0a51) layout.performClick()
                }
            }

        "com.bytedance.sdk.openadsdk.core.component.splash.countdown.TTCountdownViewForCircle".toClassOrNull()
            ?.resolve()?.method()?.build()?.forEach { method ->
                method.hook {
                    after {
                        val view = instance as View
                        view.performClick()
                    }
                }
            }
    }
}
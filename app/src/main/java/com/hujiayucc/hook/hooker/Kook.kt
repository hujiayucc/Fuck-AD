package com.hujiayucc.hook.hooker

import android.view.View
import android.widget.LinearLayout
import com.highcapable.yukihookapi.hook.factory.method
import com.highcapable.yukihookapi.hook.type.android.LinearLayoutClass
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
        LinearLayoutClass.method { name = "onDraw" }.hook {
                after {
                    val layout = instance as LinearLayout
                    if (layout.id == 0x7f0a0a51) layout.performClick()
                }
            }

        "com.bytedance.sdk.openadsdk.core.component.splash.countdown.TTCountdownViewForCircle".toClassOrNull()
            ?.method { name = "getView" }
            ?.hook {
                after {
                    val view = instance as View
                    view.performClick()
                }
            }
    }
}
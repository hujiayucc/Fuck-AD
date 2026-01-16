package com.hujiayucc.hook.hooker.app

import android.widget.LinearLayout
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.hujiayucc.hook.annotation.RunJiaGu
import com.hujiayucc.hook.hooker.util.Base

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
        loadSdk(pangle = true)
        LinearLayout::class.resolve().firstMethod { name = "onDraw" }
            .hook {
                after {
                    val layout = instance as LinearLayout
                    if (layout.id == 0x7f0a0a51) layout.performClick()
                }
            }
    }
}
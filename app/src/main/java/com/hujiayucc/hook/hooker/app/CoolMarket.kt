package com.hujiayucc.hook.hooker.app

import android.view.View
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.hujiayucc.hook.annotation.RunJiaGu
import com.hujiayucc.hook.hooker.Base

@RunJiaGu(
    appName = "酷安",
    packageName = "com.coolapk.market",
    action = "禁用SDK, 信息流广告"
)
object CoolMarket : Base() {
    override fun onStart() {
        loadSdk(pangle = true)
        "androidx.appcompat.widget.AppCompatImageView".toClassOrNull()
            ?.resolve()?.firstMethod { name = "hasOverlappingRendering" }
            ?.hook {
                after {
                    val view = instance as View
                    if (view.id == 0x7f0b0424) {
                        if (view.isClickable && view.performClick()) {
                            debug("信息流广告")
                        }
                    }
                }
            }
    }
}
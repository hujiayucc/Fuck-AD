package com.hujiayucc.hook.hooker

import android.view.View
import com.highcapable.yukihookapi.hook.factory.allMethods
import com.hujiayucc.hook.annotation.RunJiaGu

@RunJiaGu(
    appName = "疯狂刷题",
    packageName = "com.yaerxing.fkst",
    action = "开屏广告"
)
object FengKuangShuaTi: Base() {
    override fun onStart() {
        "com.bytedance.sdk.openadsdk.core.component.splash.countdown.TTCountdownViewForCircle".toClassOrNull()
            ?.allMethods { _, method ->
                method.hook {
                    after {
                        val view = instance as View
                        if (view.isClickable) view.performClick()
                    }
                }
            }
    }
}
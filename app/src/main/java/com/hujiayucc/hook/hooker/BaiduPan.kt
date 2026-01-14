package com.hujiayucc.hook.hooker

import android.annotation.SuppressLint
import android.view.View
import android.widget.TextView
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.hujiayucc.hook.annotation.Run

@Run(
    appName = "百度网盘",
    packageName = "com.baidu.netdisk",
    action = "开屏广告",
    versions = [
        "13.8.0"
    ]
)
object BaiduPan : Base() {
    @SuppressLint("ResourceType")
    override fun onStart() {
        "com.qumeng.advlib.__remote__.ui.elements.SplashCountdownView".toClass()
            .resolve().method().build().forEach { method ->
                method.hook {
                    after {
                        val view = instance as View
                        if (view.isClickable) view.performClick()
                    }
                }
            }

        "com.bytedance.sdk.openadsdk.core.component.splash.countdown.TTCountdownViewForCircle".toClass()
            .resolve().method().build().forEach { method ->
                method.hook {
                    after {
                        val view = instance as View
                        view.performClick()
                    }
                }
            }

        "com.beizi.fusion.widget.SkipView".toClass()
            .resolve().firstMethod { name = "onTextChanged" }
            .hook {
                after {
                    val view = instance as TextView
                    view.performClick()
                }
            }

        "com.baidu.sdk.container.widget.RectangleCountDownView".toClass()
            .resolve().method().build().forEach { method ->
                method.hook {
                    after {
                        val view = instance as View
                        if (view.isClickable) view.performClick()
                    }
                }
            }
    }
}
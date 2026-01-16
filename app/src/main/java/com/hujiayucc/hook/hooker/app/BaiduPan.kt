package com.hujiayucc.hook.hooker.app

import android.annotation.SuppressLint
import android.view.View
import android.widget.TextView
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.hujiayucc.hook.annotation.Run
import com.hujiayucc.hook.hooker.Base

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
        loadSdk(pangle = true)
        "com.qumeng.advlib.__remote__.ui.elements.SplashCountdownView".toClass()
            .resolve().method().build().forEach { method ->
                method.hook {
                    after {
                        val view = instance as View
                        if (view.isClickable) view.performClick()
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
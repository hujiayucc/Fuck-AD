package com.hujiayucc.hook.hooker

import android.annotation.SuppressLint
import android.view.View
import android.widget.TextView
import com.highcapable.yukihookapi.hook.factory.allMethods
import com.highcapable.yukihookapi.hook.factory.method
import com.hujiayucc.hook.annotation.Run

@Run(
    "百度网盘",
    "com.baidu.netdisk",
    "开屏广告",
    [
        "13.8.0"
    ]
)
object BaiduPan : Base() {
    @SuppressLint("ResourceType")
    override fun onStart() {
        "com.qumeng.advlib.__remote__.ui.elements.SplashCountdownView".toClass()
            .allMethods { index, method ->
                method.hook {
                    after {
                        val view = instance as View
                        if (view.isClickable) view.performClick()
                    }
                }
            }

        "com.bytedance.sdk.openadsdk.core.component.splash.countdown.TTCountdownViewForCircle".toClass()
            .method { name = "getView" }
            .hook {
                after {
                    val view = instance as View
                    view.performClick()
                }
            }

        "com.beizi.fusion.widget.SkipView".toClass()
            .method { name = "onTextChanged" }
            .hook {
                after {
                    val view = instance as TextView
                    view.performClick()
                }
            }

        "com.baidu.sdk.container.widget.RectangleCountDownView".toClass()
            .allMethods { index, method ->
                method.hook {
                    after {
                        val view = instance as View
                        if (view.isClickable) view.performClick()
                    }
                }
            }
    }
}
package com.hujiayucc.hook.hooker

import android.view.View
import android.widget.TextView
import com.highcapable.yukihookapi.hook.factory.constructor
import com.highcapable.yukihookapi.hook.factory.method
import com.hujiayucc.hook.annotation.Run

@Run(
    appName = "百度贴吧",
    packageName = "com.baidu.tieba",
    action = "开屏广告, 信息流广告",
    versions = [
        "12.86.1.0"
    ]
)
object TieBa : Base() {
    override fun onStart() {
        "com.baidu.sdk.container.widget.RectangleCountDownView".toClass().method {
            name = "onDraw"
        }.hook {
            after {
                val textView = instance as TextView
                val text = textView.text.toString()
                val regex = Regex("^跳过(.*)\\d$")
                if (regex.matches(text)) {
                    textView.performClick()
                }
            }
        }

        "com.baidu.tieba.recapp.lego.view.AdCardBaseView".toClass().constructor()
            .hook {
                after {
                    val view = instance as View
                    view.visibility = View.GONE
                }
            }
    }
}
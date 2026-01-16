package com.hujiayucc.hook.hooker.app

import android.view.View
import android.widget.TextView
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.hujiayucc.hook.annotation.Run
import com.hujiayucc.hook.hooker.util.Base

@Run(
    appName = "百度贴吧",
    packageName = "com.baidu.tieba",
    action = "开屏广告, 信息流广告"
)
object TieBa : Base() {
    override fun onStart() {
        "com.baidu.sdk.container.widget.RectangleCountDownView".toClass().resolve().firstMethod {
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

        "com.baidu.tieba.recapp.lego.view.AdCardBaseView".toClass().resolve()
            .constructor().build().forEach { constructor ->
                constructor.hook {
                    after {
                        val view = instance as View
                        view.visibility = View.GONE
                    }
                }
            }
    }
}
package com.hujiayucc.hook.hooker.app

import android.view.View
import android.widget.TextView
import com.hujiayucc.hook.annotation.Run
import com.hujiayucc.hook.hooker.util.Hooker
import io.github.libxposed.api.XposedModuleInterface

@Run(
    appName = "百度贴吧",
    packageName = "com.baidu.tieba",
    action = "开屏广告, 信息流广告"
)
object TieBa : Hooker() {
    private val skipCountdownRegex = Regex("^跳过(.*)\\d$")

    override fun XposedModuleInterface.PackageReadyParam.onPackageReady() {
        "com.baidu.sdk.container.widget.RectangleCountDownView".toClassOrNull()
            ?.methodOrNull("onDraw")
            ?.hook {
                after {
                    val textView = instance as? TextView ?: return@after
                    val text = textView.text.toString()
                    if (skipCountdownRegex.matches(text)) {
                        textView.performClick()
                    }
                }
            }

        "com.baidu.tieba.recapp.lego.view.AdCardBaseView".toClassOrNull()
            ?.constructor()?.forEach { constructor ->
                constructor.hook {
                    after {
                        val view = instance as? View ?: return@after
                        view.visibility = View.GONE
                    }
                }
            }
    }
}
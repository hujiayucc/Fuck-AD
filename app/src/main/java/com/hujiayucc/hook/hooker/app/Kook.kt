package com.hujiayucc.hook.hooker.app

import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.TextView
import com.hujiayucc.hook.annotation.RunJiaGu
import com.hujiayucc.hook.hooker.util.Hooker
import io.github.libxposed.api.XposedModuleInterface

@RunJiaGu(
    appName = "KOOK",
    packageName = "cn.kaiheila",
    action = "开屏广告",
    versions = [
        "1.75.0"
    ]
)
object Kook : Hooker() {
    override val jiaGuMarkerClasses = listOf(
        "cj.mobile.jt.core.ui.widget.CountdownView"
    )

    override fun XposedModuleInterface.PackageReadyParam.onPackageReady() {
        TextView::class.java.method("onDraw").hook {
            after {
                val textView = instance<TextView>()
                if (textView.id == 0x70030083) {
                    runMain { textView.performClick() }
                }
            }
        }

        View::class.java.method("onDraw").hook {
            after {
                val view = instance<View>()
                if (view.javaClass.name == "cj.mobile.jt.core.ui.widget.CountdownView") {
                    runMain { view.performClick() }
                }
            }
        }
    }
}
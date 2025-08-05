package com.hujiayucc.hook.hooker

import android.app.Dialog
import com.highcapable.yukihookapi.hook.factory.method
import com.highcapable.yukihookapi.hook.type.android.DialogClass
import com.hujiayucc.hook.annotation.Run

@Run(
    "次元城动画",
    "com.yaxisvip.pubgtool.iueg",
    "彈窗广告",
    [
        "4.2.8-ga5770ca"
    ]
)
object CYC : Base() {
    override fun onStart() {
        "com.windmill.sdk.a.m$12$1".toClass()
            .method { name = "run" }
            .hook {
                replaceUnit {}
            }

        DialogClass.method { name = "show" }
            .hook {
                after {
                    if (instance::class.java.name == "androidx.compose.ui.window.DialogWrapper") {
                        val dialog = instance as Dialog
                        dialog.dismiss()
                    }
                }
            }
    }
}
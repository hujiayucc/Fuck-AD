package com.hujiayucc.hook.hooker

import com.highcapable.yukihookapi.hook.factory.method
import com.hujiayucc.hook.annotation.Run

@Run(
    "网易有道云词典",
    "com.youdao.dict",
    "开屏广告",
    [
        "10.2.19"
    ]
)
object YouDaoDict : Base() {
    override fun onStart() {
        if (versionName == "10.2.19")
            "com.youdao.community.extension.ExtensionsKt".toClassOrNull()
                ?.method { name = "H" }
                ?.hook {
                    after {
                        val view = args[0] as android.view.View
                        view.performClick()
                    }
                }
    }
}
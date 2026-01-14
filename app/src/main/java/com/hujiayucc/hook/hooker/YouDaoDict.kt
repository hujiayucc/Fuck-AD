package com.hujiayucc.hook.hooker

import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.hujiayucc.hook.annotation.Run

@Run(
    appName = "网易有道云词典",
    packageName = "com.youdao.dict",
    action = "开屏广告",
    versions = [
        "10.2.19"
    ]
)
object YouDaoDict : Base() {
    override fun onStart() {
        if (versionName == "10.2.19")
            "com.youdao.community.extension.ExtensionsKt".toClassOrNull()
                ?.resolve()?.firstMethod { name = "H" }
                ?.hook {
                    after {
                        val view = args[0] as android.view.View
                        view.performClick()
                    }
                }
    }
}
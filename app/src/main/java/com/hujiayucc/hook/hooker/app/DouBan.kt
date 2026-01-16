package com.hujiayucc.hook.hooker.app

import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.hujiayucc.hook.annotation.Run
import com.hujiayucc.hook.hooker.Base

@Run(
    appName = "豆瓣",
    packageName = "com.douban.frodo",
    action = "开屏广告",
    versions = [
        "7.105.1"
    ]
)
object DouBan : Base() {
    override fun onStart() {
        loadSdk(pangle = true, gdt = true)
        "com.douban.ad.h0".toClassOrNull()
            ?.resolve()?.firstMethod { name = "run" }
            ?.hook { replaceUnit {} }

        "com.douban.ad.g0".toClassOrNull()
            ?.resolve()?.firstMethod { name = "run" }
            ?.hook { replaceUnit {} }

        "com.douban.ad.k0".toClassOrNull()
            ?.resolve()?.firstMethod { name = "d" }
            ?.hook { replaceUnit {} }

        "com.douban.ad.AdView".toClassOrNull()
            ?.resolve()?.firstMethod { name = "b" }
            ?.hook { replaceUnit {} }

        "com.douban.ad.t".toClassOrNull()
            ?.resolve()?.firstMethod { name = "onGlobalLayout" }
            ?.hook { replaceUnit {} }

        $$"com.douban.frodo.splash.SplashAdNewRequestor$c".toClassOrNull()
            ?.resolve()?.firstMethod { name = "handleMessage" }
            ?.hook { replaceUnit {} }
    }
}
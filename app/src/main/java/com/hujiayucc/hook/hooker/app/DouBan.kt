package com.hujiayucc.hook.hooker.app

import com.hujiayucc.hook.annotation.Run
import com.hujiayucc.hook.hooker.sdk.GDT
import com.hujiayucc.hook.hooker.sdk.Pangle
import com.hujiayucc.hook.hooker.util.Hooker
import io.github.libxposed.api.XposedModuleInterface

@Run(
    appName = "豆瓣",
    packageName = "com.douban.frodo",
    action = "开屏广告",
    versions = [
        "7.105.1"
    ]
)
object DouBan : Hooker() {
    override fun XposedModuleInterface.PackageReadyParam.onPackageReady() {
        Pangle.call(this)
        GDT.call(this)
        "com.douban.ad.h0".toClassOrNull()
            ?.method("run")
            ?.hook { replaceUnit {} }

        "com.douban.ad.g0".toClassOrNull()
            ?.method("run")
            ?.hook { replaceUnit {} }

        "com.douban.ad.k0".toClassOrNull()
            ?.method("d")
            ?.hook { replaceUnit {} }

        "com.douban.ad.AdView".toClassOrNull()
            ?.method("b")
            ?.hook { replaceUnit {} }

        "com.douban.ad.t".toClassOrNull()
            ?.method("onGlobalLayout")
            ?.hook { replaceUnit {} }

        $$"com.douban.frodo.splash.SplashAdNewRequestor$c".toClassOrNull()
            ?.method("handleMessage")
            ?.hook { replaceUnit {} }
    }
}
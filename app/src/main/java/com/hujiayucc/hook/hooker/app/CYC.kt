package com.hujiayucc.hook.hooker.app

import com.hujiayucc.hook.annotation.Run
import com.hujiayucc.hook.hooker.util.Hooker
import io.github.libxposed.api.XposedModuleInterface

@Run(
    appName = "次元城动画",
    packageName = "com.yaxisvip.pubgtool.iueg",
    action = "彈窗广告",
    versions = [
        "4.2.8-ga5770ca"
    ]
)
object CYC : Hooker() {
    override fun XposedModuleInterface.PackageReadyParam.onPackageReady() {
        "com.windmill.sdk.a.m$12$1".toClassOrNull()
            ?.methodOrNull("run")
            ?.hook {
                replaceUnit {}
            }
    }
}
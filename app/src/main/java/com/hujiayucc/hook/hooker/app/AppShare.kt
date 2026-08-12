package com.hujiayucc.hook.hooker.app

import com.hujiayucc.hook.annotation.Run
import com.hujiayucc.hook.hooker.util.Hooker
import io.github.libxposed.api.XposedModuleInterface

@Run(
    appName = "AppShare",
    packageName = "info.muge.appshare",
    action = "广告",
    versions = [
        "5.0.9"
    ]
)
object AppShare : Hooker() {
    override fun XposedModuleInterface.PackageReadyParam.onPackageReady() {
        "info.muge.appshare.view.launch.LaunchBean".toClassOrNull()
            ?.methods("getHaveAd")
            .hook {
                replaceTo(false)
            }
    }
}
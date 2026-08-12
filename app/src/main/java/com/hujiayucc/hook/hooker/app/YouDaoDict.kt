package com.hujiayucc.hook.hooker.app

import android.view.View
import com.hujiayucc.hook.annotation.Run
import com.hujiayucc.hook.hooker.util.Hooker
import io.github.libxposed.api.XposedModuleInterface

@Run(
    appName = "网易有道云词典",
    packageName = "com.youdao.dict",
    action = "开屏广告",
    versions = [
        "10.2.19"
    ]
)
object YouDaoDict : Hooker() {
    override fun XposedModuleInterface.PackageReadyParam.onPackageReady() {
        "com.youdao.community.extension.ExtensionsKt".toClassOrNull()
            ?.method("H")
            ?.hook {
                after {
                    val view = args[0] as View
                    view.performClick()
                }
            }
    }
}
package com.hujiayucc.hook.hooker.app

import com.hujiayucc.hook.annotation.Run
import com.hujiayucc.hook.hooker.util.Hooker
import io.github.libxposed.api.XposedModuleInterface

@Run(
    appName = "虎牙直播",
    packageName = "com.duowan.kiwi",
    action = "开屏广告"
)
object HuYa : Hooker() {
    override fun XposedModuleInterface.PackageReadyParam.onPackageReady() {
        loadSdk(this, kw = true)
    }
}
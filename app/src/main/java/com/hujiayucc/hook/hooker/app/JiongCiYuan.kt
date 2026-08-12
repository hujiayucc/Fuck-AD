package com.hujiayucc.hook.hooker.app

import com.hujiayucc.hook.annotation.Run
import com.hujiayucc.hook.hooker.util.Hooker
import io.github.libxposed.api.XposedModuleInterface


@Run(
    appName = "囧次元",
    packageName = "com.jumang.jiongciyuan",
    action = "开屏广告"
)
object JiongCiYuan : Hooker() {
    override fun XposedModuleInterface.PackageReadyParam.onPackageReady() {
        "com.jumang.jiongciyuan.homepage.bean.UpgradeBean".toClassOrNull()
            ?.method("getStatus")
            ?.hook {
                replaceTo(0)
            }
    }
}

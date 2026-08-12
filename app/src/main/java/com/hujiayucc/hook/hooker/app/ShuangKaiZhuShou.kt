package com.hujiayucc.hook.hooker.app

import android.content.Context
import com.hujiayucc.hook.annotation.Run
import com.hujiayucc.hook.hooker.util.Hooker
import io.github.libxposed.api.XposedModuleInterface

@Run(
    appName = "双开助手微分身版",
    packageName = "com.excelliance.dualaid",
    action = "解锁会员"
)
object ShuangKaiZhuShou : Hooker() {
    override fun XposedModuleInterface.PackageReadyParam.onPackageReady() {
        // 解锁会员
        "com.excelliance.dualaid.ppp.VvvM".toClassOrNull()
            ?.method("i", Context::class.java)
            ?.hook {
                replaceTo(true)
            }
    }
}

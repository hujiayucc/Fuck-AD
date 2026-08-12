package com.hujiayucc.hook.hooker.app

import android.app.Activity
import com.hujiayucc.hook.annotation.Run
import com.hujiayucc.hook.hooker.util.Hooker
import io.github.libxposed.api.XposedModuleInterface

@Run(
    appName = "Soul",
    packageName = "cn.soulapp.android",
    action = "开屏广告"
)
object Soul : Hooker() {
    override fun XposedModuleInterface.PackageReadyParam.onPackageReady() {
        "cn.soulapp.android.ad.ui.HotAdActivity".toClassOrNull()
            ?.method("onCreate")
            ?.hook {
                before {
                    instance<Activity>().finish()
                }
            }
    }
}
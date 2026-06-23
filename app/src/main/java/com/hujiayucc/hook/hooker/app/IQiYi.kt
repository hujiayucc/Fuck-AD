package com.hujiayucc.hook.hooker.app

import android.view.View
import com.hujiayucc.hook.annotation.Run
import com.hujiayucc.hook.hooker.util.Hooker
import io.github.libxposed.api.XposedModuleInterface

@Run(
    appName = "爱奇艺",
    packageName = "com.qiyi.video",
    action = "开屏广告",
    versions = [
        "16.7.5"
    ]
)
object IQiYi : Hooker() {
    override fun XposedModuleInterface.PackageReadyParam.onPackageReady() {
        "com.qiyi.video.qysplashscreen.ad.g".toClassOrNull()
            ?.methodOrNull("B1")
            ?.hook {
                after {
                    val view = getField(instance, "t") as? View ?: return@after
                    view.performClick()
                }
            }
    }
}
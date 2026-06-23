package com.hujiayucc.hook.hooker.app

import android.app.Activity
import android.content.Intent
import com.hujiayucc.hook.annotation.Run
import com.hujiayucc.hook.hooker.util.Hooker
import io.github.libxposed.api.XposedModuleInterface

@Run(
    appName = "伴生活",
    packageName = "com.banshenghuo.mobile",
    action = "开屏广告",
    versions = [
        "2.6.25.004"
    ]
)
object BanShengHuo : Hooker() {
    override fun XposedModuleInterface.PackageReadyParam.onPackageReady() {
        "com.banshenghuo.mobile.modules.SplashActivity".toClassOrNull()
            ?.methods("initData")
            .hook {
                replaceUnit {
                    val activity = instance<Activity>()
                    val intent =
                        Intent(activity, "com.banshenghuo.mobile.modules.MainActivity".toClass())
                    activity.startActivity(intent)
                    activity.finish()
                }
            }
    }
}
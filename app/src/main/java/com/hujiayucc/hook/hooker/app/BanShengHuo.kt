package com.hujiayucc.hook.hooker.app

import android.app.Activity
import android.content.Intent
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.hujiayucc.hook.annotation.Run
import com.hujiayucc.hook.hooker.Base

@Run(
    appName = "伴生活",
    packageName = "com.banshenghuo.mobile",
    action = "开屏广告",
    versions = [
        "2.6.25.004"
    ]
)
object BanShengHuo : Base() {
    override fun onStart() {
        "com.banshenghuo.mobile.modules.SplashActivity".toClass()
            .resolve().firstMethod { name = "initData" }
            .hook {
                replaceUnit {
                    val activity = instance as Activity
                    val intent =
                        Intent(activity, "com.banshenghuo.mobile.modules.MainActivity".toClass())
                    activity.startActivity(intent)
                    activity.finish()
                }
            }
    }
}
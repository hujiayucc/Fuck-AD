package com.hujiayucc.hook.hooker

import android.app.Activity
import android.content.Intent
import com.highcapable.yukihookapi.hook.factory.method
import com.hujiayucc.hook.annotation.Run

@Run(
    "伴生活",
    "com.banshenghuo.mobile",
    "开屏广告",
    [
        "2.6.25.004"
    ]
)
object BanShengHuo : Base() {
    override fun onStart() {
        "com.banshenghuo.mobile.modules.SplashActivity".toClass()
            .method { name = "initData" }
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
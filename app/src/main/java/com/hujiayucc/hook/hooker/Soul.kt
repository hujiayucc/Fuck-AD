package com.hujiayucc.hook.hooker

import android.app.Activity
import com.highcapable.yukihookapi.hook.factory.method
import com.hujiayucc.hook.annotation.Run

@Run(
    "Soul",
    "cn.soulapp.android",
    "开屏广告"
)
object Soul : Base() {
    override fun onStart() {
        "cn.soulapp.android.ad.ui.HotAdActivity".toClassOrNull()
            ?.method { name = "onCreate" }
            ?.hook {
                before {
                    instance<Activity>().finish()
                }
            }
    }
}
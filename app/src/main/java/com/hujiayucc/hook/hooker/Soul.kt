package com.hujiayucc.hook.hooker

import android.app.Activity
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.hook.factory.method
import com.hujiayucc.hook.annotation.Run

@Run(
    appName = "Soul",
    packageName = "cn.soulapp.android",
    action = "开屏广告"
)
object Soul : Base() {
    override fun onStart() {
        "cn.soulapp.android.ad.ui.HotAdActivity".toClassOrNull()
            ?.resolve()?.firstMethod { name = "onCreate" }
            ?.hook {
                before {
                    instance<Activity>().finish()
                }
            }
    }
}
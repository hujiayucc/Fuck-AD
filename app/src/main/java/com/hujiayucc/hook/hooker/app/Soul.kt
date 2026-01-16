package com.hujiayucc.hook.hooker.app

import android.app.Activity
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.hujiayucc.hook.annotation.Run
import com.hujiayucc.hook.hooker.util.Base

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
package com.hujiayucc.hook.hook.app

import android.app.Activity
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.factory.method

object GSWW: YukiBaseHooker() {
    override fun onHook() {
        "local.z.androidshared.vip.RewardActivity".toClass().method {
            name = "onCreate"
        }.hook().before {
            val activity = instance<Activity>()
            activity.finish()
        }
    }
}
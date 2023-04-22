package com.hujiayucc.hook.hook.app

import android.app.Activity
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker

object GSWW: YukiBaseHooker() {
    override fun onHook() {
        findClass("local.z.androidshared.vip.RewardActivity").hook {
            injectMember {
                method { name = "onCreate" }
                beforeHook {
                    val activity = instance<Activity>()
                    activity.finish()
                }
            }
        }
    }
}
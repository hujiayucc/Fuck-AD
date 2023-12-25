package com.hujiayucc.hook.hook.app

import android.app.Activity
import android.content.Intent
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.factory.method

/** 米游社 */
object MiYoHyper: YukiBaseHooker() {
    override fun onHook() {
        "com.mihoyo.hyperion.ui.SplashActivity".toClass().method {
            name = "onCreate"
        }.hook().replaceUnit {
            val activity = instance<Activity>()
            val context = activity.applicationContext
            val intent = Intent(context,"com.mihoyo.hyperion.main.HyperionMainActivity".toClass())
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
            activity.finish()
        }
    }
}
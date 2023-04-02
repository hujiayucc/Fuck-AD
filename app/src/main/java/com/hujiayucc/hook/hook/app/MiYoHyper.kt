package com.hujiayucc.hook.hook.app

import android.app.Activity
import android.content.Intent
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker

/** 米游社 */
object MiYoHyper: YukiBaseHooker() {
    override fun onHook() {
        findClass("com.mihoyo.hyperion.ui.SplashActivity").hook {
            injectMember {
                method { name = "onCreate" }
                replaceUnit {
                    val activity = instance<Activity>()
                    val context = activity.applicationContext
                    val intent = Intent(context,"com.mihoyo.hyperion.main.HyperionMainActivity".toClass())
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    context.startActivity(intent)
                    activity.finish()
                }
            }
        }
    }
}
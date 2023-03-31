package com.hujiayucc.hook.hook.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.type.android.ApplicationClass

/** 米游社 */
object MiYoHyper: YukiBaseHooker() {
    override fun onHook() {
        ApplicationClass.hook {
            injectMember {
                method { name = "onCreate" }
                afterHook {
                    val context = instance<Context>()
                    val intent = Intent(context,"com.mihoyo.hyperion.main.HyperionMainActivity".toClass())
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    context.startActivity(intent)
                }
            }
        }

        findClass("com.mihoyo.hyperion.ui.SplashActivity").hook {
            injectMember {
                method { name = "onCreate" }
                replaceUnit {
                    val activity = instance<Activity>()
                    activity.finish()
                }
            }
        }
    }
}
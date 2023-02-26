package com.hujiayucc.hook.hook.app

import android.app.Application
import android.content.Intent
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.type.android.ApplicationClass

object AppShare : YukiBaseHooker() {
    override fun onHook() {
        ApplicationClass.hook {
            injectMember {
                method { name = "onCreate" }
                afterHook {
                    val context = instance<Application>()
                    val intent = Intent(context, "info.muge.appshare.view.main.MainActivity".toClass(appClassLoader))
                        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                }
            }
        }
    }
}
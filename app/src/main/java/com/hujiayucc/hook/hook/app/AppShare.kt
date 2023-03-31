package com.hujiayucc.hook.hook.app

import android.app.Activity
import android.content.Intent
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker

/** App分享 */
object AppShare : YukiBaseHooker() {
    override fun onHook() {
        findClass("info.muge.appshare.view.launch.LaunchActivity").hook {
            injectMember {
                method { name = "a" }
                replaceUnit {
                    val activity = instance<Activity>()
                    val intent = Intent(activity, "info.muge.appshare.view.main.MainActivity".toClass(appClassLoader))
                        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    activity.startActivity(intent)
                    activity.finish()
                }
            }
        }
    }
}
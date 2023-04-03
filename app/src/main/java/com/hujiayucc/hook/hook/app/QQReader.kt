package com.hujiayucc.hook.hook.app

import android.app.Activity
import android.content.Intent
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker

/** QQ阅读 */
object QQReader: YukiBaseHooker() {
    override fun onHook() {
        findClass("com.qq.reader.activity.SplashActivity").hook {
            injectMember {
                method { name = "onCreate" }
                beforeHook {
                    val activity = instance<Activity>()
                    val intent = Intent(
                        activity.applicationContext,
                        "com.qq.reader.activity.MainActivity".toClass(appClassLoader)
                    )
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    activity.applicationContext.startActivity(intent)
                    activity.finish()
                }
            }
        }.ignoredHookClassNotFoundFailure()
    }
}
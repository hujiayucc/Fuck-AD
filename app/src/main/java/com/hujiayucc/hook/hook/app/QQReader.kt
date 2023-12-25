package com.hujiayucc.hook.hook.app

import android.app.Activity
import android.content.Intent
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.factory.method

/** QQ阅读 */
object QQReader: YukiBaseHooker() {
    override fun onHook() {
        "com.qq.reader.activity.SplashActivity".toClass().method {
            name = "onCreate"
        }.hook().before {
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
}
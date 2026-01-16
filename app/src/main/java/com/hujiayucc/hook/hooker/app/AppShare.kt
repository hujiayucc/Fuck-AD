package com.hujiayucc.hook.hooker.app

import android.app.Activity
import android.content.Intent
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.hujiayucc.hook.annotation.Run
import com.hujiayucc.hook.hooker.util.Base

@Run(appName = "AppShare", packageName = "info.muge.appshare", action = "开屏广告")
object AppShare : Base() {
    override fun onStart() {
        "info.muge.appshare.view.launch.LaunchActivity".toClass()
            .resolve().firstMethod {
                name = "initView"
            }.hook {
                after {
                    val activity = instance<Activity>()
                    activity.startActivityForResult(
                        Intent(activity, "info.muge.appshare.view.main.MainActivity".toClass()), -1
                    )
                    activity.finish()
                }
            }.ignoredAllFailure()
    }
}
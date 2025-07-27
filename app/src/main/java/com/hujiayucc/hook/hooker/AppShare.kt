package com.hujiayucc.hook.hooker

import android.app.Activity
import android.content.Intent
import com.highcapable.yukihookapi.hook.factory.method
import com.hujiayucc.hook.annotation.Run

@Run("AppShare", "info.muge.appshare", "开屏广告")
object AppShare : Base() {
    override fun onStart() {
        "info.muge.appshare.view.launch.LaunchActivity".toClass()
            .method { name = "initView" }.hook {
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
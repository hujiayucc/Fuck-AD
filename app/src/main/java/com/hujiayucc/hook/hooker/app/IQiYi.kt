package com.hujiayucc.hook.hooker.app

import android.view.View
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.hujiayucc.hook.annotation.Run
import com.hujiayucc.hook.hooker.Base
import de.robv.android.xposed.XposedHelpers

@Run(
    appName = "爱奇艺",
    packageName = "com.qiyi.video",
    action = "开屏广告",
    versions = [
        "16.7.5"
    ]
)
object IQiYi : Base() {
    override fun onStart() {
        "com.qiyi.video.qysplashscreen.ad.g".toClass()
            .resolve().firstMethod { name = "B1" }
            .hook {
                after {
                    val view = XposedHelpers.getObjectField(instance, "t") as View
                    view.performClick()
                }
            }
    }
}
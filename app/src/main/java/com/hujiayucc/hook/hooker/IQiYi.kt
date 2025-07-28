package com.hujiayucc.hook.hooker

import android.view.View
import com.highcapable.yukihookapi.hook.factory.method
import com.hujiayucc.hook.annotation.Run
import de.robv.android.xposed.XposedHelpers

@Run(
    "爱奇艺",
    "com.qiyi.video",
    "开屏广告",
    [
        "16.7.5"
    ]
)
object IQiYi : Base() {
    override fun onStart() {
        "com.qiyi.video.qysplashscreen.ad.g".toClass()
            .method { name = "B1" }
            .hook {
                after {
                    val view = XposedHelpers.getObjectField(instance, "t") as View
                    view.performClick()
                }
            }
    }
}
package com.hujiayucc.hook.hooker.app

import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.hujiayucc.hook.annotation.Run
import com.hujiayucc.hook.hooker.Base

@Run(
    appName = "次元城动画",
    packageName = "com.yaxisvip.pubgtool.iueg",
    action = "彈窗广告",
    versions = [
        "4.2.8-ga5770ca"
    ]
)
object CYC : Base() {
    override fun onStart() {
        "com.windmill.sdk.a.m$12$1".toClass()
            .resolve().firstMethod { name = "run" }
            .hook {
                replaceUnit {}
            }
    }
}
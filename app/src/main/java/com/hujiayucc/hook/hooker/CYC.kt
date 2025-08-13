package com.hujiayucc.hook.hooker

import com.highcapable.yukihookapi.hook.factory.method
import com.hujiayucc.hook.annotation.Run

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
            .method { name = "run" }
            .hook {
                replaceUnit {}
            }
    }
}
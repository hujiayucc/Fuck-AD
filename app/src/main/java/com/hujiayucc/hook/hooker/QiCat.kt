package com.hujiayucc.hook.hooker

import android.view.View
import com.highcapable.yukihookapi.hook.factory.method
import com.hujiayucc.hook.annotation.Run
import de.robv.android.xposed.XposedHelpers

@Run(
    "七猫免费小说",
    "com.kmxs.reader",
    "开屏广告",
    [
        "7.67"
    ]
)
object QiCat : Base() {
    override fun onStart() {
        if (versionName == "7.67")
            "com.qimao.qmad.qmsdk.splash.SplashAdFragmentNew".toClassOrNull()
                ?.method { name = "r0" }
                ?.hook {
                    after {
                        val view = XposedHelpers.getObjectField(instance, "s") as View
                        view.performClick()
                    }
                }
    }
}
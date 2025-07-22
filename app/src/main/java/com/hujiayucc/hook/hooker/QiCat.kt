package com.hujiayucc.hook.hooker

import android.view.View
import com.highcapable.yukihookapi.hook.factory.method
import com.hujiayucc.hook.annotation.Run
import de.robv.android.xposed.XposedHelpers

@Run("七猫免费小说", "com.kmxs.reader")
object QiCat : Base() {
    override fun onStart() {
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
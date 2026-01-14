package com.hujiayucc.hook.hooker

import android.app.Activity
import android.os.Handler
import android.os.Looper
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.hujiayucc.hook.annotation.Run
import de.robv.android.xposed.XposedHelpers

@Run(
    appName = "抖音",
    packageName = "com.ss.android.ugc.aweme",
    action = "奖励广告（小程序广告除外）"
)
object DouYin : Base() {

    override fun onStart() {
        "com.ss.android.excitingvideo.ExcitingVideoActivity".toClassOrNull()
            ?.resolve()?.firstMethod { name = "onResume" }
            ?.hook {
                after {
                    instance.javaClass.fields.forEach { field ->
                        if (field.type.name == "com.ss.android.excitingvideo.sdk.ExcitingVideoFragment") {
                            val obj = XposedHelpers.getObjectField(instance, field.name)
                            Handler(Looper.getMainLooper()).postDelayed(obj.runnable(instance), 1000)
                            return@after
                        }
                    }
                }
            }
    }

    private fun Any.runnable(any: Any) = Runnable {
        if (!check()) return@Runnable
        if (any is Activity) any.finish()
    }

    fun Any.check(): Boolean {
        try {
            val method = XposedHelpers.findMethodExactIfExists(javaClass, "sendRewardWhenLiveNotAvailable")
            if (method != null) {
                method.isAccessible = true
                method.invoke(this)
                return true
            }
        } catch (_: Exception) {
        }
        return false
    }
}
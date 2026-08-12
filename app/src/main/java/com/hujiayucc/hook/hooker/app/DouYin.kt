package com.hujiayucc.hook.hooker.app

import android.app.Activity
import com.hujiayucc.hook.annotation.Run
import com.hujiayucc.hook.hooker.util.Hooker
import io.github.libxposed.api.XposedModuleInterface

@Run(
    appName = "抖音",
    packageName = "com.ss.android.ugc.aweme",
    action = "奖励广告（小程序广告除外）"
)
object DouYin : Hooker() {

    override fun XposedModuleInterface.PackageReadyParam.onPackageReady() {
        "com.ss.android.excitingvideo.ExcitingVideoActivity".toClassOrNull()
            ?.method("onResume")
            ?.hook {
                after {
                    instance.javaClass.fields.forEach { field ->
                        if (field.type.name == "com.ss.android.excitingvideo.sdk.ExcitingVideoFragment") {
                            val activity = instance as? Activity
                            val fragment = getField(instance, field.name)
                            if (fragment == null) {
                                logW("Unable to read ExcitingVideoFragment field: ${field.name}")
                                return@after
                            }
                            runMainDelayed(1000) {
                                if (fragment.sendReward()) {
                                    activity?.finish()
                                }
                            }
                            return@after
                        }
                    }
                }
            }
    }

    private fun Any.sendReward(): Boolean {
        return runCatching {
            javaClass.method("sendRewardWhenLiveNotAvailable").apply {
                isAccessible = true
            }.invoke(this)
        }.onFailure { error ->
            logW("Failed to send DouYin exciting video reward", error)
        }.isSuccess
    }
}
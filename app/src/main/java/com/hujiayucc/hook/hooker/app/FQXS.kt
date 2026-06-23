package com.hujiayucc.hook.hooker.app

import com.hujiayucc.hook.annotation.Run
import com.hujiayucc.hook.data.Data.formatTime
import com.hujiayucc.hook.hooker.util.Hooker
import io.github.libxposed.api.XposedModuleInterface

@Run(
    appName = "番茄免费小说",
    packageName = "com.dragon.read",
    action = "会员、广告"
)
object FQXS : Hooker() {
    override fun XposedModuleInterface.PackageReadyParam.onPackageReady() {
        "com.dragon.read.user.model.VipInfoModel".toClassOrNull()
            ?.constructor()
            ?.forEach { constructor ->
                constructor.hook {
                    after {
                        val expireTime = "2099-12-31".formatTime()
                        setField(instance, "expireTime", "${expireTime.time / 1000}")
                        setField(instance, "isAdVip", true)
                        setField(instance, "isVip", "1")
                        setField(instance, "leftTime", "${expireTime.time / 1000}")
                    }
                }
            }
    }
}
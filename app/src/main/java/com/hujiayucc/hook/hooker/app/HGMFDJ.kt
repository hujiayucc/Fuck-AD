package com.hujiayucc.hook.hooker.app

import com.hujiayucc.hook.annotation.Run
import com.hujiayucc.hook.hooker.util.Hooker
import io.github.libxposed.api.XposedModuleInterface

@Run(
    appName = "红果免费短剧",
    packageName = "com.phoenix.read",
    action = "会员、广告"
)
object HGMFDJ : Hooker() {
    override fun XposedModuleInterface.PackageReadyParam.onPackageReady() {
        "com.dragon.read.user.model.VipInfoModel".toClassOrNull()
            ?.constructor()?.forEach { constructor ->
                constructor.hook {
                    after {
                        setField(instance, "expireTime", "4102444799")
                        setField(instance, "isVip", "1")
                        setField(instance, "leftTime", "1")
                        setField(instance, "isAutoCharge", true)
                        setField(instance, "isUnionVip", true)
                        setField(instance, "unionSource", 1)
                        setField(instance, "isAdVip", true)
                        setField(instance, "subType", args[7])
                    }
                }
            }

    }
}
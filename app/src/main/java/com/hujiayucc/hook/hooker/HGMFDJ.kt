package com.hujiayucc.hook.hooker

import com.highcapable.yukihookapi.hook.factory.constructor
import com.hujiayucc.hook.annotation.Run
import de.robv.android.xposed.XposedHelpers

@Run(
    appName = "红果免费短剧",
    packageName = "com.phoenix.read",
    action = "会员、广告"
)
object HGMFDJ : Base() {
    override fun onStart() {
        "com.dragon.read.user.model.VipInfoModel".toClass()
            .constructor()
            .hook {
                after {
                    XposedHelpers.setObjectField(instance, "expireTime", "4102444799")
                    XposedHelpers.setObjectField(instance, "isVip", "1")
                    XposedHelpers.setObjectField(instance, "leftTime", "1")
                    XposedHelpers.setBooleanField(instance, "isAutoCharge", true)
                    XposedHelpers.setBooleanField(instance, "isUnionVip", true)
                    XposedHelpers.setIntField(instance, "unionSource", 1)
                    XposedHelpers.setBooleanField(instance, "isAdVip", true)
                    XposedHelpers.setObjectField(instance, "subType", args[7])
                }
            }
    }
}
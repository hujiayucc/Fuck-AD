package com.hujiayucc.hook.hooker

import com.highcapable.yukihookapi.hook.factory.constructor
import com.hujiayucc.hook.annotation.Run
import de.robv.android.xposed.XposedHelpers
import java.text.SimpleDateFormat

@Run(
    appName = "番茄免费小说",
    packageName = "com.dragon.read",
    action = "会员、广告"
)
object FQXS : Base() {
    override fun onStart() {
        "com.dragon.read.user.model.VipInfoModel".toClass().constructor().hook {
            after {
                val forMat = SimpleDateFormat("yyyy-MM-dd")
                val expireTime = forMat.parse("2099-12-31")
                XposedHelpers.setObjectField(instance, "expireTime", "${expireTime!!.time / 1000}")
                XposedHelpers.setBooleanField(instance, "isAdVip", true)
                XposedHelpers.setObjectField(instance, "isVip", "1")
                XposedHelpers.setObjectField(instance, "leftTime", "${expireTime.time / 1000}")
            }
        }
    }
}
package com.hujiayucc.hook.hooker.app

import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.hujiayucc.hook.annotation.Run
import com.hujiayucc.hook.data.Data.formatTime
import com.hujiayucc.hook.hooker.util.Base
import de.robv.android.xposed.XposedHelpers

@Run(
    appName = "番茄免费小说",
    packageName = "com.dragon.read",
    action = "会员、广告"
)
object FQXS : Base() {
    override fun onStart() {
        "com.dragon.read.user.model.VipInfoModel".toClass().resolve().constructor().build()
            .forEach { constructor ->
                constructor.hook {
                    after {
                        val expireTime = "2099-12-31".formatTime()
                        XposedHelpers.setObjectField(instance, "expireTime", "${expireTime.time / 1000}")
                        XposedHelpers.setBooleanField(instance, "isAdVip", true)
                        XposedHelpers.setObjectField(instance, "isVip", "1")
                        XposedHelpers.setObjectField(instance, "leftTime", "${expireTime.time / 1000}")
                    }
                }
            }
    }
}
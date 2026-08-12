package com.hujiayucc.hook.hooker.app

import com.hujiayucc.hook.annotation.Run
import com.hujiayucc.hook.hooker.util.Hooker
import io.github.libxposed.api.XposedModuleInterface

@Run(
    appName = "彩云天气",
    packageName = "com.nowcasting.activity",
    action = "解锁会员"
)
object CaiYunTianQi : Hooker() {
    override fun XposedModuleInterface.PackageReadyParam.onPackageReady() {
        val userInfo = "com.nowcasting.entity.UserInfo".toClassOrNull()
        // 设置是会员
        userInfo?.methodOrNull("setVIP")
            ?.hook {
                before {
                    val mArgs = args.toMutableList()
                    mArgs[0] = true
                    result = proceedWith(mArgs.toTypedArray())
                }
            }
        // 设置会员类型 超级会员
        userInfo?.methodOrNull("setVip_type")
            ?.hook {
                before {
                    val mArgs = args.toMutableList()
                    mArgs[0] = "svip"
                    result = proceedWith(mArgs.toTypedArray())
                }
            }
        // 设置超级会员到期时间
        userInfo?.methodOrNull("setSvip_expired_at")
            ?.hook {
                before {
                    val mArgs = args.toMutableList()
                    mArgs[0] = 4701859200L
                    result = proceedWith(mArgs.toTypedArray())
                }
            }
    }
}

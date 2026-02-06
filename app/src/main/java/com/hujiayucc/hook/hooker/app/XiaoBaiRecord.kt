package com.hujiayucc.hook.hooker.app

import android.app.Application
import android.content.Context
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.hujiayucc.hook.annotation.Run
import com.hujiayucc.hook.hooker.util.Base
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.query.enums.StringMatchType

@Run(
    appName = "小白录屏",
    packageName = "com.xiaobai.screen.record",
    action = "解锁会员"
)
object XiaoBaiRecord : Base() {
    override fun onStart() {
        val boolMap = emptyMap<String, String>().toMutableMap()
        DexKitBridge.create(appInfo.sourceDir).use { bridge ->
            // 设置会员
            bridge.findMethod {
                // com.dream.era.global.cn.network.SettingsManager.d()
                searchPackages("com.dream.era.global.cn.network")
                matcher {
                    returnType = "boolean"
                    addUsingString("key_debug_force_vip", StringMatchType.Equals)
                }
            }

            // 免登录
            bridge.findMethod {
                // com.dream.era.global.cn.keep.GlobalSDKImpl.isLogin()
                searchPackages("com.dream.era.global.cn.keep")
                matcher {
                    name = "isLogin"
                    returnType = "boolean"
                }
            }
            bridge.close()
        }

        Application::class.resolve().firstMethod { name = "attach" }.hook {
            before {
                for ((className, methodName) in boolMap) {
                    className.toClassOrNull((args[0] as Context).classLoader)?.resolve()?.firstMethod { name = methodName }
                        ?.hook { replaceTo(true) }?.ignoredAllFailure()
                }
            }
        }
    }
}
package com.hujiayucc.hook.hooker.app

import com.hujiayucc.hook.ModuleMain
import com.hujiayucc.hook.annotation.Run
import com.hujiayucc.hook.hooker.util.Hooker
import io.github.libxposed.api.XposedModuleInterface
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.query.enums.StringMatchType

@Run(
    appName = "小白录屏",
    packageName = "com.xiaobai.screen.record",
    action = "解锁会员"
)
object XiaoBaiRecord : Hooker() {
    override fun XposedModuleInterface.PackageReadyParam.onPackageReady() {
        if (!ModuleMain.ensureDexKitLoaded()) {
            logHookDebug("Skip $appName because DexKit native library is unavailable")
            return
        }

        DexKitBridge.create(applicationInfo.sourceDir).use { bridge ->
            // 设置会员
            bridge.findMethod {
                // com.dream.era.global.cn.network.SettingsManager.d()
                searchPackages("com.dream.era.global.cn.network")
                matcher {
                    returnType = "boolean"
                    addUsingString("key_debug_force_vip", StringMatchType.Equals)
                }
            }.forEach { method ->
                method.getMethodInstance(classLoader).hook { replaceTo(true) }
            }

            // 免登录
            bridge.findMethod {
                // com.dream.era.global.cn.keep.GlobalSDKImpl.isLogin()
                searchPackages("com.dream.era.global.cn.keep")
                matcher {
                    name = "isLogin"
                    returnType = "boolean"
                }
            }.forEach { method ->
                method.getMethodInstance(classLoader).hook { replaceTo(true) }
            }
            bridge.close()
        }
    }
}

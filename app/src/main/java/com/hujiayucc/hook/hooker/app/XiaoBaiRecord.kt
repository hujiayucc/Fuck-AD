package com.hujiayucc.hook.hooker.app

import com.hujiayucc.hook.ModuleMain
import com.hujiayucc.hook.annotation.Run
import com.hujiayucc.hook.hooker.util.DexKitMethodCache
import com.hujiayucc.hook.hooker.util.Hooker
import io.github.libxposed.api.XposedModuleInterface
import java.lang.reflect.Method
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.query.enums.StringMatchType

@Run(
    appName = "小白录屏",
    packageName = "com.xiaobai.screen.record",
    action = "解锁会员"
)
object XiaoBaiRecord : Hooker() {
    private const val QUERY_FORCE_VIP = "force_vip"
    private const val QUERY_IS_LOGIN = "is_login"
    private const val TARGET_PACKAGE = "com.xiaobai.screen.record"

    override fun XposedModuleInterface.PackageReadyParam.onPackageReady() {
        val apkPath = applicationInfo.sourceDir
        val targetClassLoader = classLoader ?: return
        val cachedMethods = listOf(
            cachedMethod(apkPath, QUERY_FORCE_VIP),
            cachedMethod(apkPath, QUERY_IS_LOGIN)
        )
        if (cachedMethods.all { it != null }) {
            cachedMethods.filterNotNull().forEach { method -> method.hook { replaceTo(true) } }
            return
        }

        if (!ModuleMain.ensureDexKitLoaded()) {
            logHookDebug("Skip $appName because DexKit native library is unavailable")
            return
        }

        DexKitBridge.create(apkPath).use { bridge ->
            bridge.findForceVipMethod(targetClassLoader)?.also { method ->
                cacheMethod(apkPath, QUERY_FORCE_VIP, method)
                method.hook { replaceTo(true) }
            }
            bridge.findLoginMethod(targetClassLoader)?.also { method ->
                cacheMethod(apkPath, QUERY_IS_LOGIN, method)
                method.hook { replaceTo(true) }
            }
        }
    }

    private fun cachedMethod(apkPath: String, queryId: String): Method? {
        return DexKitMethodCache.get(ModuleMain.prefs, TARGET_PACKAGE, apkPath, queryId, classLoader ?: return null)
    }

    private fun cacheMethod(apkPath: String, queryId: String, method: Method) {
        DexKitMethodCache.put(ModuleMain.prefs, TARGET_PACKAGE, apkPath, queryId, method)
    }

    private fun DexKitBridge.findForceVipMethod(targetClassLoader: ClassLoader): Method? {
        return findMethod {
            searchPackages("com.dream.era.global.cn.network")
            matcher {
                returnType = "boolean"
                addUsingString("key_debug_force_vip", StringMatchType.Equals)
            }
        }.singleOrNull()?.getMethodInstance(targetClassLoader)
    }

    private fun DexKitBridge.findLoginMethod(targetClassLoader: ClassLoader): Method? {
        return findMethod {
            searchPackages("com.dream.era.global.cn.keep")
            matcher {
                name = "isLogin"
                returnType = "boolean"
            }
        }.singleOrNull()?.getMethodInstance(targetClassLoader)
    }
}
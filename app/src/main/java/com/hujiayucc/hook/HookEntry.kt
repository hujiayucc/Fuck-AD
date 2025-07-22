package com.hujiayucc.hook

import android.content.Context
import com.highcapable.yukihookapi.YukiHookAPI
import com.highcapable.yukihookapi.annotation.xposed.InjectYukiHookWithXposed
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.factory.method
import com.highcapable.yukihookapi.hook.log.YLog
import com.highcapable.yukihookapi.hook.param.PackageParam
import com.highcapable.yukihookapi.hook.type.android.ApplicationClass
import com.highcapable.yukihookapi.hook.type.java.ThreadClass
import com.highcapable.yukihookapi.hook.xposed.proxy.IYukiHookXposedInit
import com.hujiayucc.hook.annotation.Run
import com.hujiayucc.hook.author.JwtUtils.isLogin
import com.hujiayucc.hook.hooker.DumpDex
import com.hujiayucc.hook.hooker.Sdks
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.query.enums.StringMatchType

/** Hook入口 */
@InjectYukiHookWithXposed
class HookEntry : IYukiHookXposedInit {
    companion object {
        init {
            System.loadLibrary("dexkit")
        }
    }

    override fun onInit() = YukiHookAPI.configs {
        debugLog {
            tag = "Fuck AD"
            isEnable = BuildConfig.DEBUG
            isRecord = BuildConfig.DEBUG
        }

        isDebug = BuildConfig.DEBUG
        isEnableModuleAppResourcesCache = true
        isEnableDataChannel = false
    }

    override fun onHook() = YukiHookAPI.encase {
        if (!prefs.isLogin()) return@encase
        val moduleClassLoader = this::class.java.classLoader
        DexKitBridge.create(moduleAppFilePath).use { bridge ->
            bridge.findClass {
                searchPackages("com.hujiayucc.hook.hooker")
                matcher {
                    annotations {
                        add {
                            type = Run::class.java.name
                            addElement {
                                name = "packageName"
                                stringValue(packageName, StringMatchType.Equals)
                            }
                        }
                    }
                }
            }.forEach { data ->
                val hooker = moduleClassLoader?.let { classLoader ->
                    data.getInstance(classLoader).getDeclaredConstructor()
                        ?.newInstance()
                }
                hooker?.let { h -> loadHooker(h as YukiBaseHooker) }
                ApplicationClass.method { name = "attach" }
                    .hook {
                        before {
                            val context = args[0] as Context
                            appClassLoader = context.classLoader
                            if (prefs.getBoolean("sdk")) loadHooker(DumpDex(context))
                            hooker?.let { h -> loadHooker(h as YukiBaseHooker) }
                        }
                    }
            }
        }

        if (prefs.getBoolean("exception")) dispatchUncaughtException()
        if (prefs.getBoolean("sdk")) loadHooker(Sdks)
    }

    /** 拦截未处理的异常 */
    private fun PackageParam.dispatchUncaughtException() {
        ThreadClass.method { name = "dispatchUncaughtException" }
            .hook {
                replaceUnit {
                    if (!BuildConfig.DEBUG) return@replaceUnit
                    val param = args[0] as Throwable?
                    param?.message?.let { YLog.error(it, param) }
                }
            }
    }
}
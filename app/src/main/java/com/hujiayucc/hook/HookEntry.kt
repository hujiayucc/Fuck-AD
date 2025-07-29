package com.hujiayucc.hook

import com.highcapable.yukihookapi.YukiHookAPI
import com.highcapable.yukihookapi.annotation.xposed.InjectYukiHookWithXposed
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.factory.method
import com.highcapable.yukihookapi.hook.factory.prefs
import com.highcapable.yukihookapi.hook.log.YLog
import com.highcapable.yukihookapi.hook.param.PackageParam
import com.highcapable.yukihookapi.hook.type.java.ThreadClass
import com.highcapable.yukihookapi.hook.xposed.proxy.IYukiHookXposedInit
import com.hujiayucc.hook.annotation.Run
import com.hujiayucc.hook.annotation.RunJiaGu
import com.hujiayucc.hook.author.JwtUtils.isLogin
import com.hujiayucc.hook.hooker.ClickInfo
import com.hujiayucc.hook.hooker.DumpDex
import de.robv.android.xposed.XposedHelpers
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
        if (packageName == BuildConfig.APPLICATION_ID) {
            "com.hujiayucc.hook.ui.activity.MainActivity".toClass()
                .method { name = "onCreate" }
                .hook {
                    after {
                        XposedHelpers.callMethod(
                            instance,
                            "updateFrameworkStatus",
                            YukiHookAPI.Status.Executor.name,
                            YukiHookAPI.Status.Executor.apiLevel
                        )
                    }
                }
            return@encase
        }

        val moduleClassLoader = this::class.java.classLoader
        val bridge = DexKitBridge.create(moduleAppFilePath)
        var isJiaGi = false

        onAppLifecycle {
            attachBaseContext { context, hasCalledSuper ->
                if (hasCalledSuper) return@attachBaseContext
                appClassLoader = context.classLoader
                if (prefs.getBoolean("dump")) loadHooker(DumpDex(context))
                bridge.findClass {
                    searchPackages("com.hujiayucc.hook.hooker")
                    matcher {
                        annotations {
                            add {
                                type = RunJiaGu::class.java.name
                                addElement {
                                    name = "packageName"
                                    stringValue(packageName, StringMatchType.Equals)
                                }
                            }
                        }
                    }
                }.forEach { data ->
                    isJiaGi = true
                    val hooker = moduleClassLoader?.let { classLoader ->
                        data.getInstance(classLoader).getDeclaredConstructor()
                            ?.newInstance()
                    }
                    hooker?.let { h -> loadHooker(h as YukiBaseHooker) }
                }
                if (prefs.getBoolean("exception")) dispatchUncaughtException()
                if (prefs.getBoolean("clickInfo")) loadHooker(ClickInfo)
            }
        }

        if (appContext?.prefs()?.isLogin() == false) return@encase

        if (!isJiaGi) {
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
            }
            if (prefs.getBoolean("exception")) dispatchUncaughtException()
            if (prefs.getBoolean("clickInfo")) loadHooker(ClickInfo)
            if (prefs.getBoolean("dump")) appContext?.let { loadHooker(DumpDex(it)) }
        }
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
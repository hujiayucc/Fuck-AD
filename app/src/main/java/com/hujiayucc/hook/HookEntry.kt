package com.hujiayucc.hook

import android.content.Context
import com.highcapable.yukihookapi.YukiHookAPI
import com.highcapable.yukihookapi.annotation.xposed.InjectYukiHookWithXposed
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.factory.method
import com.highcapable.yukihookapi.hook.factory.prefs
import com.highcapable.yukihookapi.hook.log.YLog
import com.highcapable.yukihookapi.hook.param.PackageParam
import com.highcapable.yukihookapi.hook.type.android.ApplicationClass
import com.highcapable.yukihookapi.hook.type.java.ThreadClass
import com.highcapable.yukihookapi.hook.xposed.proxy.IYukiHookXposedInit
import com.hujiayucc.hook.annotation.Run
import com.hujiayucc.hook.author.JwtUtils.isLogin
import com.hujiayucc.hook.hooker.DumpDex
import com.hujiayucc.hook.hooker.Sdks
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
        }
        if (appContext?.prefs()?.isLogin() == false) return@encase
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
                            if (appContext?.prefs()?.getBoolean("sdk") == true) loadHooker(
                                DumpDex(
                                    context
                                )
                            )
                            hooker?.let { h -> loadHooker(h as YukiBaseHooker) }
                        }
                    }
            }
        }

        if (appContext?.prefs()?.getBoolean("exception") == true) dispatchUncaughtException()
        if (appContext?.prefs()?.getBoolean("sdk") == true) loadHooker(Sdks)
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
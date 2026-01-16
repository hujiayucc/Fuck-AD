package com.hujiayucc.hook

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.YukiHookAPI
import com.highcapable.yukihookapi.annotation.xposed.InjectYukiHookWithXposed
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.factory.registerModuleAppActivities
import com.highcapable.yukihookapi.hook.xposed.proxy.IYukiHookXposedInit
import com.hujiayucc.hook.annotation.Run
import com.hujiayucc.hook.annotation.RunJiaGu
import com.hujiayucc.hook.author.JwtUtils.isLogin
import com.hujiayucc.hook.data.Data.prefsBridge
import com.hujiayucc.hook.data.Data.proxyMap
import com.hujiayucc.hook.hooker.util.ClickInfo
import com.hujiayucc.hook.hooker.util.DumpDex
import com.hujiayucc.hook.ui.activity.MainActivity
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
        }

        isEnableModuleAppResourcesCache = false
        isEnableDataChannel = false
        isEnableHookSharedPreferences = true
    }

    override fun onHook() = YukiHookAPI.encase {
        if (!isFirstApplication) return@encase
        val moduleClassLoader = this::class.java.classLoader
        val bridge = DexKitBridge.create(moduleAppFilePath)
        var isJiaGi = false

        onAppLifecycle {
            attachBaseContext { context, _ ->
                appClassLoader = context.classLoader
                if (!context.prefsBridge.isLogin()) return@attachBaseContext
                if (context.prefsBridge.getBoolean("dump")) loadHooker(DumpDex(context))
                bridge.findClass {
                    searchPackages("com.hujiayucc.hook.hooker.app")
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
                        data.getInstance(classLoader).getDeclaredConstructor().newInstance()
                    }
                    hooker?.let { h -> loadHooker(h as YukiBaseHooker) }
                }

                if (!isJiaGi) {
                    bridge.findClass {
                        searchPackages("com.hujiayucc.hook.hooker.app")
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
                            data.getInstance(classLoader).getDeclaredConstructor().newInstance()
                        }
                        hooker?.let { h -> loadHooker(h as YukiBaseHooker) }
                    }
                    loadHooker(ClickInfo)
                    if (context.prefsBridge.getBoolean("dump")) appContext?.let { loadHooker(DumpDex(it)) }
                }

                loadHooker(ClickInfo)
            }

            onCreate {
                if (packageName != BuildConfig.APPLICATION_ID &&
                    packageName != "android" &&
                    prefsBridge.getBoolean("hostPrompt", true)
                ) {
                    registerModuleAppActivities(proxyMap.getOrDefault(packageName, null))
                    Activity::class.resolve().firstMethod {
                        name = "onCreate"
                        parameters(Bundle::class.java)
                    }.hook {
                        after {
                            val activity = instance<Activity>()
                            if (activity is MainActivity) return@after
                            val intent = Intent(activity, MainActivity::class.java)
                            activity.startActivity(intent)
                        }
                    }
                }
            }
        }
    }
}
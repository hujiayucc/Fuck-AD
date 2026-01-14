package com.hujiayucc.hook

import android.content.Intent
import com.highcapable.yukihookapi.YukiHookAPI
import com.highcapable.yukihookapi.annotation.xposed.InjectYukiHookWithXposed
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.factory.prefs
import com.highcapable.yukihookapi.hook.factory.registerModuleAppActivities
import com.highcapable.yukihookapi.hook.xposed.proxy.IYukiHookXposedInit
import com.hujiayucc.hook.annotation.Run
import com.hujiayucc.hook.annotation.RunJiaGu
import com.hujiayucc.hook.author.JwtUtils.isLogin
import com.hujiayucc.hook.hooker.ClickInfo
import com.hujiayucc.hook.hooker.DumpDex
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
            isRecord = BuildConfig.DEBUG
        }

        isDebug = BuildConfig.DEBUG
        isEnableModuleAppResourcesCache = true
        isEnableDataChannel = false
    }

    override fun onHook() = YukiHookAPI.encase {
        if (!isFirstApplication) return@encase
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
                        data.getInstance(classLoader).getDeclaredConstructor().newInstance()
                    }
                    hooker?.let { h -> loadHooker(h as YukiBaseHooker) }
                }
                loadHooker(ClickInfo)
            }

            onCreate {
                if (
                    !prefs.isLogin() ||
                    packageName != BuildConfig.APPLICATION_ID &&
                    prefs.getBoolean("hostPrompt", true)
                ) {
                    registerModuleAppActivities()
                    val intent = Intent(this, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    startActivity(intent)
                }
            }
        }

        if (appContext?.prefs()?.native()?.isLogin() == false) return@encase

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
                    data.getInstance(classLoader).getDeclaredConstructor().newInstance()
                }
                hooker?.let { h -> loadHooker(h as YukiBaseHooker) }
            }
            loadHooker(ClickInfo)
            if (prefs.getBoolean("dump")) appContext?.let { loadHooker(DumpDex(it)) }
        }
    }
}
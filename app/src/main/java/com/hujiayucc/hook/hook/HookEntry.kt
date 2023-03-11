package com.hujiayucc.hook.hook

import android.content.Context
import com.highcapable.yukihookapi.YukiHookAPI
import com.highcapable.yukihookapi.annotation.xposed.InjectYukiHookWithXposed
import com.highcapable.yukihookapi.hook.factory.toClassOrNull
import com.highcapable.yukihookapi.hook.param.PackageParam
import com.highcapable.yukihookapi.hook.xposed.proxy.IYukiHookXposedInit
import com.hujiayucc.hook.BuildConfig
import com.hujiayucc.hook.data.Data.global
import com.hujiayucc.hook.data.Data.hookTip
import com.hujiayucc.hook.data.Data.themes
import com.hujiayucc.hook.data.PackageName
import com.hujiayucc.hook.hook.app.*
import com.hujiayucc.hook.hook.app.DragonRead.hook
import com.hujiayucc.hook.hook.entity.Provider
import com.hujiayucc.hook.hook.sdk.Google
import com.hujiayucc.hook.hook.sdk.KWAD
import com.hujiayucc.hook.hook.sdk.Pangle
import com.hujiayucc.hook.hook.sdk.Tencent
import com.hujiayucc.hook.utils.HookTip
import com.hujiayucc.hook.utils.Log

/** Hook入口 */
@InjectYukiHookWithXposed
object HookEntry : IYukiHookXposedInit {
    override fun onHook() = YukiHookAPI.encase {
        if (YukiHookAPI.Status.isModuleActive && packageName != BuildConfig.APPLICATION_ID) {
            if (prefs.get(global)) {
                loadApp(packageName) {
                    load(this)
                }
            } else {
                if (prefs.getBoolean(packageName, true)) {
                    loadApp(packageName) {
                        load(this)
                    }
                }
            }
        } else if (packageName.equals(BuildConfig.APPLICATION_ID)) {
            if (YukiHookAPI.Status.isModuleActive) {
                loadApp(BuildConfig.APPLICATION_ID) {
                    resources().hook {
                        injectResource {
                            conditions {
                                name = "theme"
                                color()
                            }

                            replaceTo(prefs.get(themes))
                        }
                    }
                }
            }
        }
    }

    /** 加载规则 */
    private fun load(packageParam: PackageParam) {
        // Hook成功提示
        if (packageParam.prefs.get(hookTip))
            HookTip.show(packageParam)
        // 适配堆糖专属规则
        if (packageParam.packageName.equals(PackageName.DuiTang)) {
            packageParam.loadHooker(DuiTang)
            return
        }
        // 最右
        if (packageParam.packageName.equals(PackageName.ZuiYou)) packageParam.loadHooker(ZuiYou)
        // 番茄小说
        if (packageParam.packageName.equals(PackageName.DragonRead)) packageParam.loadHooker(DragonRead)
        // 喜马拉雅
        if (packageParam.packageName.equals(PackageName.XiMaLaYa)) packageParam.loadHooker(XiMaLaYa)
        // App分享
        if (packageParam.packageName.equals(PackageName.AppShare)) packageParam.loadHooker(AppShare)
        // 360加固
        if ("com.stub.StubApp".toClassOrNull(packageParam.appClassLoader) != null) {
            Log.d("360加固")
            packageParam.findClass("com.stub.StubApp").hook {
                injectMember {
                    method { name = "attachBaseContext" }
                    afterHook {
                        val context = args[0] as Context
                        packageParam.appClassLoader = context.classLoader
                        // 腾讯广告
                        packageParam.loadHooker(Tencent)
                        // 穿山甲广告
                        packageParam.loadHooker(Pangle)
                        // 快手广告
                        packageParam.loadHooker(KWAD)
                        // 禁用广告SDK Provider
                        packageParam.loadHooker(Provider)
                        packageParam.loadHooker(Google)
                    }
                }
            }.ignoredHookClassNotFoundFailure()
        } else if ("com.wrapper.proxyapplication.WrapperProxyApplication".toClassOrNull(packageParam.appClassLoader) != null) {
            Log.d("腾讯御安全")
            packageParam.findClass("com.wrapper.proxyapplication.WrapperProxyApplication").hook {
                injectMember {
                    method { name = "attachBaseContext" }
                    afterHook {
                        val context = args[0] as Context
                        packageParam.appClassLoader = context.classLoader
                        // 腾讯广告
                        packageParam.loadHooker(Tencent)
                        // 穿山甲广告
                        packageParam.loadHooker(Pangle)
                        // 快手广告
                        packageParam.loadHooker(KWAD)
                        // 禁用广告SDK Provider
                        packageParam.loadHooker(Provider)
                        packageParam.loadHooker(Google)
                    }
                }
            }.ignoredHookClassNotFoundFailure()
        } else {
            Log.d("非360加固")
            // 腾讯广告
            packageParam.loadHooker(Tencent)
            // 穿山甲广告
            packageParam.loadHooker(Pangle)
            // 快手广告
            packageParam.loadHooker(KWAD)
            // 禁用广告SDK Provider
            packageParam.loadHooker(Provider)
            packageParam.loadHooker(Google)
        }
    }
}
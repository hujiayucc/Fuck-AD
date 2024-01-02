package com.hujiayucc.hook.hook

import android.content.Context
import com.highcapable.yukihookapi.YukiHookAPI
import com.highcapable.yukihookapi.annotation.xposed.InjectYukiHookWithXposed
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.factory.method
import com.highcapable.yukihookapi.hook.factory.toClassOrNull
import com.highcapable.yukihookapi.hook.param.PackageParam
import com.highcapable.yukihookapi.hook.xposed.proxy.IYukiHookXposedInit
import com.hujiayucc.hook.BuildConfig
import com.hujiayucc.hook.data.Data.global
import com.hujiayucc.hook.data.Data.hookTip
import com.hujiayucc.hook.hook.app.DragonRead.hook
import com.hujiayucc.hook.hook.entity.HookerList
import com.hujiayucc.hook.hook.entity.Jiagu
import com.hujiayucc.hook.hook.entity.PrivateDns
import com.hujiayucc.hook.hook.entity.Provider
import com.hujiayucc.hook.hook.sdk.Google
import com.hujiayucc.hook.hook.sdk.KWAD
import com.hujiayucc.hook.hook.sdk.Pangle
import com.hujiayucc.hook.hook.sdk.Tencent
import com.hujiayucc.hook.utils.HookTip
import com.hujiayucc.hook.utils.Log

/** Hook入口 */
@InjectYukiHookWithXposed
class HookEntry : IYukiHookXposedInit {
    override fun onHook() = YukiHookAPI.encase {
        if (prefs.getString("session").isBlank()) return@encase
        if (packageName == "android") {
            loadSystem(PrivateDns)
            loadZygote(PrivateDns)
        }

        if (YukiHookAPI.Status.isModuleActive && packageName != BuildConfig.APPLICATION_ID && packageName != "android") {
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
        }
    }

    /** 加载规则 */
    private fun load(packageParam: PackageParam) {
        if (packageParam.packageName == "com.google.android.webview") return
        // Hook成功提示
        if (packageParam.prefs.get(hookTip) && packageParam.isFirstApplication)
            HookTip.show(packageParam)
        // 加载应用专属规则
        val hooker = HookerList.fromPackageName(packageParam.packageName)
        if (hooker != null) {
            packageParam.loadHooker(hooker["hooker"] as YukiBaseHooker)
            if (hooker["stop"] as Boolean) return
        }

        for (type in Jiagu.entries) {
            val clazz = type.packageName.toClassOrNull(packageParam.appClassLoader)
            if (clazz != null) {
                Log.d(type.type)
                clazz.method { name = "attachBaseContext" }.ignored().hook().after {
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
                    // 谷歌广告
                    packageParam.loadHooker(Google)
                }
                return
            }
        }

        Log.d("非加固应用")
        // 腾讯广告
        packageParam.loadHooker(Tencent)
        // 穿山甲广告
        packageParam.loadHooker(Pangle)
        // 快手广告
        packageParam.loadHooker(KWAD)
        // 禁用广告SDK Provider
        packageParam.loadHooker(Provider)
        // 谷歌广告
        packageParam.loadHooker(Google)
    }
}
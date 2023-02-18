package com.hujiayucc.hook.hook

import com.highcapable.yukihookapi.YukiHookAPI
import com.highcapable.yukihookapi.annotation.xposed.InjectYukiHookWithXposed
import com.highcapable.yukihookapi.hook.factory.configs
import com.highcapable.yukihookapi.hook.xposed.proxy.IYukiHookXposedInit
import com.hujiayucc.hook.BuildConfig
import com.hujiayucc.hook.data.Data.global
import com.hujiayucc.hook.hook.entity.Tencent
import com.hujiayucc.hook.utils.Log

@InjectYukiHookWithXposed
object HookEntry : IYukiHookXposedInit {

    override fun onInit() = configs {
        isDebug = BuildConfig.DEBUG
    }

    override fun onHook() = YukiHookAPI.encase {
        Log.d("HookEntry onHook")

        if (YukiHookAPI.Status.isModuleActive) {
            if (prefs.get(global)) {
                loadHooker(Tencent)
            } else {
                if (prefs.getBoolean(packageName)) {
                    loadApp(packageName) {
                        loadHooker(Tencent)
                    }
                }
            }
        }
    }
}
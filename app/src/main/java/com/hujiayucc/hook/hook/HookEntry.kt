package com.hujiayucc.hook.hook

import com.highcapable.yukihookapi.YukiHookAPI
import com.highcapable.yukihookapi.annotation.xposed.InjectYukiHookWithXposed
import com.highcapable.yukihookapi.hook.param.PackageParam
import com.highcapable.yukihookapi.hook.xposed.proxy.IYukiHookXposedInit
import com.hujiayucc.hook.BuildConfig
import com.hujiayucc.hook.data.Data.global
import com.hujiayucc.hook.data.Data.hookTip
import com.hujiayucc.hook.hook.entity.APPList
import com.hujiayucc.hook.hook.entity.Provider
import com.hujiayucc.hook.hook.entity.Service
import com.hujiayucc.hook.hook.sdk.KWAD
import com.hujiayucc.hook.hook.sdk.Pangle
import com.hujiayucc.hook.hook.sdk.Tencent
import com.hujiayucc.hook.utils.HookTip

@InjectYukiHookWithXposed
object HookEntry : IYukiHookXposedInit {
    override fun onHook() = YukiHookAPI.encase {
        if (YukiHookAPI.Status.isModuleActive) {
            if (prefs.get(global) && !packageName.equals(BuildConfig.APPLICATION_ID)) {
                loadApp(packageName) {
                    load(this)
                }
            } else {
                if (prefs.getBoolean(packageName) && !packageName.equals(BuildConfig.APPLICATION_ID)) {
                    loadApp(packageName) {
                        load(this)
                    }
                }
            }
        }
    }

    /** 加载规则 */
    private fun load(packageParam: PackageParam) {
        /** Hook成功提示 */
        if (packageParam.prefs.get(hookTip))
            HookTip.show(packageParam)

        /** 禁用广告SDK Service */
        packageParam.loadHooker(Service)
        /** 禁用广告SDK Provider */
        packageParam.loadHooker(Provider)
        /** 加载已适配第三方应用 */
        packageParam.loadHooker(APPList)
        /** 腾讯广告 */
        packageParam.loadHooker(Tencent)
        /** 穿山甲广告 */
        packageParam.loadHooker(Pangle)
        /** 快手广告 */
        packageParam.loadHooker(KWAD)
    }
}
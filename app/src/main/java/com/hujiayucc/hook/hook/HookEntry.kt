package com.hujiayucc.hook.hook

import com.highcapable.yukihookapi.YukiHookAPI
import com.highcapable.yukihookapi.annotation.xposed.InjectYukiHookWithXposed
import com.highcapable.yukihookapi.hook.param.PackageParam
import com.highcapable.yukihookapi.hook.xposed.proxy.IYukiHookXposedInit
import com.hujiayucc.hook.BuildConfig
import com.hujiayucc.hook.data.Data.global
import com.hujiayucc.hook.data.Data.hookTip
import com.hujiayucc.hook.data.PackageName
import com.hujiayucc.hook.hook.app.DragonRead
import com.hujiayucc.hook.hook.app.DuiTang
import com.hujiayucc.hook.hook.app.ZuiYou
import com.hujiayucc.hook.hook.entity.Provider
import com.hujiayucc.hook.hook.sdk.KWAD
import com.hujiayucc.hook.hook.sdk.Pangle
import com.hujiayucc.hook.hook.sdk.Tencent
import com.hujiayucc.hook.utils.HookTip

/** Hook入口 */
@InjectYukiHookWithXposed
object HookEntry : IYukiHookXposedInit {
    override fun onHook() = YukiHookAPI.encase {
        if (YukiHookAPI.Status.isModuleActive && !packageName.equals(BuildConfig.APPLICATION_ID)) {
            if (prefs.get(global)) {
                loadApp(packageName) {
                    load(this)
                }
            } else {
                if (prefs.getBoolean(packageName)) {
                    loadApp(packageName) {
                        load(this)
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
        /** 最右 */
        if (packageParam.packageName.equals(PackageName.ZuiYou)) packageParam.loadHooker(ZuiYou)
        /** 番茄小说 */
        if (packageParam.packageName.equals(PackageName.DragonRead)) packageParam.loadHooker(DragonRead)

        // 腾讯广告
        packageParam.loadHooker(Tencent)
        // 穿山甲广告
        packageParam.loadHooker(Pangle)
        // 快手广告
        packageParam.loadHooker(KWAD)
        // 禁用广告SDK Provider
        packageParam.loadHooker(Provider)
    }
}
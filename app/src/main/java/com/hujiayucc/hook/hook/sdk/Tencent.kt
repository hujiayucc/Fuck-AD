package com.hujiayucc.hook.hook.sdk

import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.factory.method


/** 腾讯广告 */
object Tencent : YukiBaseHooker() {
    override fun onHook() {
        val adManager = "com.qq.e.comm.managers.GDTADManager".toClassOrNull()
        adManager?.method { name = "isInitialized" }?.ignored()?.hook()?.replaceToFalse()
        adManager?.method { name = "initWith" }?.ignored()?.hook()?.replaceToFalse()
        adManager?.method { name = "getInstance" }?.ignored()?.hook()?.replaceTo(null)

        val constants = "com.qq.e.comm.constants.CustomPkgConstants".toClassOrNull()
        constants?.method { name = "getADActivityName" }?.ignored()?.hook()?.replaceTo("")
        constants?.method { name = "getAssetPluginDir" }?.ignored()?.hook()?.replaceTo("")
        constants?.method { name = "getAssetPluginName" }?.ignored()?.hook()?.replaceTo("")
        constants?.method { name = "getADActivityClass" }?.ignored()?.hook()?.replaceTo(null)
    }
}
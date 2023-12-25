package com.hujiayucc.hook.hook.app

import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.factory.method

/** 腾讯动漫 */
object QQAc: YukiBaseHooker() {
    override fun onHook() {
        "com.qq.e.comm.constants.CustomPkgConstants".toClass().method {
            name = "getAssetPluginDir"
        }.hook().replaceTo("")

        "com.qq.e.comm.constants.CustomPkgConstants".toClass().method {
            name = "getAssetPluginName"
        }.hook().replaceTo("")

        "com.qq.e.comm.constants.CustomPkgConstants".toClass().method {
            name = "getADActivityName"
        }.hook().replaceTo("")

        "com.qq.e.comm.constants.CustomPkgConstants".toClass().method {
            name = "getADActivityClass"
        }.hook().replaceTo(null)
    }
}
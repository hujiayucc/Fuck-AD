package com.hujiayucc.hook.hook.app

import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.factory.allConstructors
import com.highcapable.yukihookapi.hook.factory.allMethods

/** 喜马拉雅 */
object XiMaLaYa : YukiBaseHooker() {
    override fun onHook() {
        val list = arrayOf(
            "com.ximalaya.ting.android.adsdk.adapter.JADSplashADWrapper",
            "com.ximalaya.ting.android.adsdk.adapter.base.AbstractSplashAd",
            "com.ximalaya.ting.android.host.manager.ad.thirdgamead.b\$3",
            "com.ximalaya.ting.android.host.manager.ad.thirdgamead.b\$2",
            "com.ximalaya.ting.android.host.manager.ad.thirdgamead.b"
        )

        for (clazz in list) {
            clazz.toClass().allMethods { _, method -> method.hook().replaceTo(null) }
            clazz.toClass().allConstructors { _, constructor -> constructor.hook().replaceTo(null) }
        }
    }
}
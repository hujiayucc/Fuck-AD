package com.hujiayucc.hook.hook.app

import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.factory.MembersType

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
            findClass(clazz).hook {
                injectMember {
                    allMembers(type = MembersType.ALL)
                    replaceTo(null)
                }
            }
        }
    }
}
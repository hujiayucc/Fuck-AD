package com.hujiayucc.hook.hooker.app

import android.view.View
import com.hujiayucc.hook.annotation.RunJiaGu
import com.hujiayucc.hook.hooker.util.Hooker
import io.github.libxposed.api.XposedModuleInterface

@RunJiaGu(
    appName = "七猫免费小说",
    packageName = "com.kmxs.reader",
    action = "开屏广告"
)
object QiCat : Hooker() {
    override val jiaGuMarkerClasses = listOf(
        "com.qimao.qmuser.model.entity.mine_v2.BaseInfo",
        "com.qimao.qmuser.model.entity.AdDataConfig",
        "com.qimao.qmuser.model.entity.AdPositionData"
    )

    override fun XposedModuleInterface.PackageReadyParam.onPackageReady() {
        val baseInfo = "com.qimao.qmuser.model.entity.mine_v2.BaseInfo".toClassOrNull()
        val methods = arrayOf("isVipExpired", "isVipState", "isShowYearVip")

        for (method in methods) {
            baseInfo?.methodOrNull(method)?.hook { replaceTo(true) }
        }

        "com.qimao.qmuser.model.entity.AdDataConfig".toClassOrNull()
            ?.methodOrNull("getAdvertiser")
            ?.hook { replaceTo(null) }

        "com.qimao.qmuser.model.entity.AdPositionData".toClassOrNull()
            ?.methodOrNull("getAdv")
            ?.hook { replaceTo(null) }

        "com.qimao.qmuser.view.bonus.LoginGuidePopupTask".toClassOrNull()
            ?.methodOrNull("addPopup")
            ?.hook { replaceUnit { } }

        "com.qimao.qmuser.view.VipStatusView".toClassOrNull()
            ?.methodOrNull("init")
            ?.hook {
                after {
                    instance<View>().visibility = View.GONE
                }
            }
    }
}
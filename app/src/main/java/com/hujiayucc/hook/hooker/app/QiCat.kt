package com.hujiayucc.hook.hooker.app

import android.view.View
import com.hujiayucc.hook.ModuleMain
import com.hujiayucc.hook.annotation.RunJiaGu
import com.hujiayucc.hook.hooker.util.Hooker
import io.github.libxposed.api.XposedModuleInterface
import org.luckypray.dexkit.DexKitBridge

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

    private fun DexKitBridge.add(
        searchPackages: String,
        method: String,
        type: String,
        map: MutableMap<String, String>,
        excludePackages: String = ""
    ) {
        findMethod {
            if (excludePackages.isNotEmpty()) excludePackages(excludePackages)
            if (searchPackages.isNotEmpty()) searchPackages(searchPackages)
            matcher {
                name = method
                returnType = type
            }
        }.single().let { method ->
            method.invokes.let {
                it[it.size - 1].let { data ->
                    map[data.declaredClassName] = data.name
                }
            }
        }
    }

    private fun DexKitBridge.adds(
        searchPackages: String,
        method: String,
        type: String,
        map: MutableMap<String, String>,
        excludePackages: String = ""
    ) {
        findMethod {
            if (excludePackages.isNotEmpty()) excludePackages(excludePackages)
            if (searchPackages.isNotEmpty()) searchPackages(searchPackages)
            matcher {
                name = method
                returnType = type
            }
        }.let { methods ->
            methods.forEach { method ->
                map[method.declaredClassName] = method.name
            }
        }
    }

    override fun XposedModuleInterface.PackageReadyParam.onPackageReady() {
        val boolMap = emptyMap<String, String>().toMutableMap()
        if (ModuleMain.ensureDexKitLoaded()) {
            DexKitBridge.create(applicationInfo.sourceDir).use { bridge ->
                bridge.add("org.geometerplus.android.fbreader", "isSpeechMode", "boolean", boolMap)
                bridge.add("com.qimao.qmuser.model.entity.mine_v2", "isVipStateChange", "boolean", boolMap)
                bridge.adds("", "isVipUser", "boolean", boolMap, "com.qimao.qmreader.bridge.app")
                bridge.adds("", "isBookVip", "boolean", boolMap)
                bridge.close()
            }
        } else {
            logHookDebug("Skip QiCat DexKit lookup because DexKit native library is unavailable")
        }

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
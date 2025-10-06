package com.hujiayucc.hook.hooker

import android.content.Context
import android.view.View
import com.highcapable.yukihookapi.hook.factory.method
import com.highcapable.yukihookapi.hook.type.android.ApplicationClass
import com.hujiayucc.hook.annotation.Run
import org.luckypray.dexkit.DexKitBridge

@Run(
    appName = "七猫免费小说",
    packageName = "com.kmxs.reader",
    action = "开屏广告"
)
object QiCat : Base() {
    override fun onStart() {
        val boolMap = emptyMap<String, String>().toMutableMap()
        DexKitBridge.create(appInfo.sourceDir).use { bridge ->
            bridge.add("org.geometerplus.android.fbreader", "isSpeechMode", "boolean", boolMap)
            bridge.add("com.qimao.qmuser.model.entity.mine_v2", "isVipStateChange", "boolean", boolMap)
            bridge.adds("", "isVipUser", "boolean", boolMap, "com.qimao.qmreader.bridge.app")
            bridge.adds("", "isBookVip", "boolean", boolMap)
            bridge.close()
        }

        ApplicationClass.method { name = "attach" }.hook {
            before {
                for ((className, methodName) in boolMap) {
                    className.toClassOrNull((args[0] as Context).classLoader)?.method { name = methodName }
                        ?.hook { replaceTo(true) }?.ignoredAllFailure()
                }
            }
        }

        val baseInfo = "com.qimao.qmuser.model.entity.mine_v2.BaseInfo".toClass()
        val methods = arrayOf("isVipExpired", "isVipState", "isShowYearVip")

        for (method in methods) {
            baseInfo.method { name = method }.hook { replaceToTrue() }
        }

        "com.qimao.qmuser.model.entity.AdDataConfig".toClass().method { name = "getAdvertiser" }
            .hook { replaceTo(null) }

        "com.qimao.qmuser.model.entity.AdPositionData".toClass().method { name = "getAdv" }
            .hook { replaceTo(null) }

        "com.qimao.qmuser.view.bonus.LoginGuidePopupTask".toClass().method { name = "addPopup" }
            .hook { replaceUnit { } }

        "com.qimao.qmuser.view.VipStatusView".toClass()
            .method { name = "init" }
            .hook{
                after {
                    instance<View>().visibility = View.GONE
                }
            }
    }

    private fun DexKitBridge.add(searchPackages: String, method: String, type: String, map: MutableMap<String, String>, excludePackages: String = "") {
        findMethod {
            if(excludePackages.isNotEmpty()) excludePackages(excludePackages)
            if(searchPackages.isNotEmpty()) searchPackages(searchPackages)
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

    private fun DexKitBridge.adds(searchPackages: String, method: String, type: String, map: MutableMap<String, String>, excludePackages: String = "") {
        findMethod {
            if(excludePackages.isNotEmpty()) excludePackages(excludePackages)
            if(searchPackages.isNotEmpty()) searchPackages(searchPackages)
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
}
package com.hujiayucc.hook.hook.sdk

import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.factory.allConstructors
import com.highcapable.yukihookapi.hook.factory.allMethods
import com.highcapable.yukihookapi.hook.factory.method


/** 穿山甲广告 */
object Pangle : YukiBaseHooker() {
    private val nullReplaceList = arrayOf(
        "com.bytedance.sdk.openadsdk.AdSlot",
        "com.bytedance.sdk.openadsdk.AdSlot.Builder"
    )

    override fun onHook() {
        "com.bytedance.sdk.openadsdk.TTAdConfig".toClassOrNull()?.method {
            name = "getSdkInfo"
        }?.ignored()?.hook()?.replaceTo(null)

        "com.bytedance.sdk.openadsdk.TTAdSdk".toClassOrNull()?.method {
            name = "init"
        }?.ignored()?.hook()?.before { resultNull() }

        "com.bytedance.sdk.openadsdk.TTAdSdk".toClassOrNull()?.method {
            name = "isInitSuccess"
        }?.ignored()?.hook()?.replaceToFalse()

        for (name in nullReplaceList) {
            val clazz = name.toClassOrNull() ?: continue
            clazz.allMethods { _, method ->
                method.hook().replaceTo(null)
            }

            clazz.allConstructors { _, constructor ->
                constructor.hook().replaceTo(null)
            }
        }
    }
}
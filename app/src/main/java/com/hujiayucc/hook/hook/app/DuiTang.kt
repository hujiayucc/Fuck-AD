package com.hujiayucc.hook.hook.app

import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.factory.allConstructors
import com.highcapable.yukihookapi.hook.factory.allMethods
import com.highcapable.yukihookapi.hook.factory.method
import com.hujiayucc.hook.hook.sdk.KWAD
import com.hujiayucc.hook.hook.sdk.Tencent

/** 堆糖 */
object DuiTang : YukiBaseHooker() {
    override fun onHook() {
        loadHooker(Tencent)
        loadHooker(KWAD)

        "com.bytedance.sdk.openadsdk.TTAdConfig".toClass().method {
            name = "getSdkInfo"
        }.hook().replaceTo(null)

        "com.bytedance.sdk.openadsdk.TTAdSdk".toClass().method {
            name = "isInitSuccess"
        }.hook().replaceToTrue()

        "com.bytedance.sdk.openadsdk.AdSlot".toClass().allMethods { _, method -> method.hook().after {} }
        "com.bytedance.sdk.openadsdk.AdSlot".toClass().allConstructors { _, constructor -> constructor.hook().after {} }

        "com.bytedance.sdk.openadsdk.AdSlot.Builder".toClass().allMethods { _, method -> method.hook().replaceTo(null) }
        "com.bytedance.sdk.openadsdk.AdSlot.Builder".toClass().allConstructors { _, constructor -> constructor.hook().replaceTo(null) }
    }
}
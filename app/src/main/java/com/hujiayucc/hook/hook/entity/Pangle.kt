package com.hujiayucc.hook.hook.entity

import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.hujiayucc.hook.utils.Log


/** 穿山甲广告 */
object Pangle : YukiBaseHooker() {
    override fun onHook() {
        findClass("com.bytedance.sdk.openadsdk.TTAdConfig").hook {
            injectMember {
                method {
                    name = "getSdkInfo"
                }.result {
                    onNoSuchMethod { Log.e("NoSuchMethod: com.bytedance.sdk.openadsdk.TTAdConfig.getSdkInfo()") }
                }

                afterHook {
                    result = null
                    Log.d("onHook Pangle")
                }
            }
        }.ignoredHookClassNotFoundFailure()
    }
}
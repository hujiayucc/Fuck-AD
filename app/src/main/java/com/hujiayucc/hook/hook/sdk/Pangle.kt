package com.hujiayucc.hook.hook.sdk

import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.factory.MembersType


/** 穿山甲广告 */
object Pangle : YukiBaseHooker() {
    override fun onHook() {
        findClass("com.bytedance.sdk.openadsdk.TTAdConfig").hook {
            injectMember {
                method { name = "getSdkInfo" }
                replaceTo(null)
            }
        }.ignoredHookClassNotFoundFailure()

        findClass("com.bytedance.sdk.openadsdk.TTAdSdk").hook {
            injectMember {
                method { name = "init" }
                beforeHook { resultNull() }
            }
            injectMember {
                method { name = "isInitSuccess" }
                replaceToFalse()
            }
        }.ignoredHookClassNotFoundFailure()

        findClass("com.bytedance.sdk.openadsdk.AdSlot").hook {
            injectMember {
                allMembers(type = MembersType.ALL)
                afterHook { }
            }
        }.ignoredHookClassNotFoundFailure()

        findClass("com.bytedance.sdk.openadsdk.AdSlot.Builder").hook {
            injectMember {
                allMembers(type = MembersType.ALL)
                replaceTo(null)
            }
        }.ignoredHookClassNotFoundFailure()
    }
}
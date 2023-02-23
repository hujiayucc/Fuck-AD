package com.hujiayucc.hook.hook.app

import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.factory.MembersType
import com.hujiayucc.hook.hook.sdk.KWAD
import com.hujiayucc.hook.hook.sdk.Tencent

object DuiTang : YukiBaseHooker() {
    override fun onHook() {
        loadHooker(Tencent)
        loadHooker(KWAD)
        findClass("com.bytedance.sdk.openadsdk.TTAdConfig").hook {
            injectMember {
                method { name = "getSdkInfo" }
                replaceTo(null)
            }
        }.ignoredHookClassNotFoundFailure()

        findClass("com.bytedance.sdk.openadsdk.TTAdSdk").hook {
            injectMember {
                method { name = "isInitSuccess" }
                replaceToTrue()
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
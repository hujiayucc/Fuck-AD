package com.hujiayucc.hook.hook.sdk

import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.factory.MembersType

/** 快手广告 */
object KWAD : YukiBaseHooker() {
    override fun onHook() {
        findClass("com.kwad.sdk.core.network.BaseResultData").hook {
            injectMember {
                allMembers(type = MembersType.ALL)
                replaceTo(null)
            }
        }.ignoredHookClassNotFoundFailure()

        findClass("com.kwad.components.offline.api.core.network.model.CommonOfflineCompoResultData").hook {
            injectMember {
                method { name = "isResultOk" }.onNoSuchMethod { }
                replaceToFalse()
            }
        }.ignoredHookClassNotFoundFailure()
    }
}
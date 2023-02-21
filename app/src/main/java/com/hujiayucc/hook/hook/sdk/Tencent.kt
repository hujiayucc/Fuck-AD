package com.hujiayucc.hook.hook.sdk

import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.factory.MembersType


/** 腾讯广告 */
object Tencent : YukiBaseHooker() {
    override fun onHook() {
        findClass("com.qq.e.comm.managers.GDTADManager").hook {
            injectMember {
                allMembers(type = MembersType.ALL)
                replaceTo(null)
            }
        }.ignoredHookClassNotFoundFailure()

        findClass("com.qq.e.comm.constants.CustomPkgConstants").hook {
            injectMember {
                allMembers(type = MembersType.ALL)
                replaceTo(null)
            }
        }.ignoredHookClassNotFoundFailure()
    }
}
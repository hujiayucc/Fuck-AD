package com.hujiayucc.hook.hook.sdk

import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker


/** 腾讯广告 */
object Tencent : YukiBaseHooker() {
    override fun onHook() {
        findClass("com.qq.e.comm.managers.GDTADManager").hook {
            injectMember {
                method { name = "isInitialized" }
                replaceToFalse()
            }.ignoredNoSuchMemberFailure()

            injectMember {
                method { name = "getInstance" }
                replaceTo(null)
            }.ignoredNoSuchMemberFailure()

            injectMember {
                method { name = "initWith" }
                replaceToFalse()
            }.ignoredNoSuchMemberFailure()
        }.ignoredHookClassNotFoundFailure()

        findClass("com.qq.e.comm.constants.CustomPkgConstants").hook {
            injectMember {
                method { name = "getAssetPluginDir" }
                replaceTo("")
            }.ignoredNoSuchMemberFailure()

            injectMember {
                method { name = "getAssetPluginName" }
                replaceTo("")
            }.ignoredNoSuchMemberFailure()

            injectMember {
                method { name = "getADActivityName" }
                replaceTo("")
            }.ignoredNoSuchMemberFailure()

            injectMember {
                method { name = "getADActivityClass" }
                replaceTo(null)
            }.ignoredNoSuchMemberFailure()
        }.ignoredHookClassNotFoundFailure()
    }
}
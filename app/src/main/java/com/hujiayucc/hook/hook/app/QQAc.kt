package com.hujiayucc.hook.hook.app

import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker

/** 腾讯动漫 */
object QQAc: YukiBaseHooker() {
    override fun onHook() {
        findClass("com.qq.e.comm.constants.CustomPkgConstants").hook {
            injectMember {
                method { name = "getAssetPluginDir" }
                replaceTo("")
            }

            injectMember {
                method { name = "getAssetPluginName" }
                replaceTo("")
            }

            injectMember {
                method { name = "getADActivityName" }
                replaceTo("")
            }

            injectMember {
                method { name = "getADActivityClass" }
                replaceTo(null)
            }
        }.ignoredHookClassNotFoundFailure()
    }
}
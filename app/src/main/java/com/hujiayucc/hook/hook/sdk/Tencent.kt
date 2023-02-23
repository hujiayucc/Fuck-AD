package com.hujiayucc.hook.hook.sdk

import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.factory.MembersType
import com.hujiayucc.hook.utils.Log


/** 腾讯广告 */
object Tencent : YukiBaseHooker() {
    override fun onHook() {
        findClass("com.qq.e.comm.managers.GDTADManager").hook {
            injectMember {
                method {
                    name = "getInstance"
                }.result {
                    onNoSuchMethod { Log.e("NoSuchMethod: com.qq.e.comm.managers.GDTADManager.getInstance()") }
                }

                afterHook {
                    result = null
                }
            }
        }.ignoredHookClassNotFoundFailure()

        findClass("com.qq.e.comm.constants.CustomPkgConstants").hook {
            injectMember {
                allMembers(type = MembersType.ALL)
                replaceTo("")
            }
        }.ignoredHookClassNotFoundFailure()
    }
}
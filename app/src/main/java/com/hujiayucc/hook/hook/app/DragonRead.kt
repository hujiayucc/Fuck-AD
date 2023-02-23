package com.hujiayucc.hook.hook.app

import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker

/** 番茄小说 */
object DragonRead : YukiBaseHooker() {
    override fun onHook() {
        /** 番茄小说听书底部直播间广告 */
        findClass("com.dragon.read.reader.speech.a.a").hook {
            injectMember {
                method {
                    name = "a"
                    paramCount = 3
                }
                replaceTo(null)
            }
        }.ignoredHookClassNotFoundFailure()
    }
}
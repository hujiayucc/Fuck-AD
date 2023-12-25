package com.hujiayucc.hook.hook.app

import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.factory.allConstructors
import com.highcapable.yukihookapi.hook.factory.allMethods
import com.highcapable.yukihookapi.hook.factory.method

/** 番茄小说 */
object DragonRead : YukiBaseHooker() {
    private val nullReplaceList = arrayOf(
        "com.dragon.read.reader.ad.readflow.ui.ReadFlowDynamicAdLine",
        "com.dragon.read.reader.ad.readflow.ui.ReadFlowDynamicAdLine"
    )
    override fun onHook() {
        /** 番茄小说听书底部直播间广告 */
        "com.dragon.read.reader.speech.a.a".toClass().method {
            name = "a"
            paramCount = 3
        }.hook().replaceTo(null)

        for (name in nullReplaceList) {
            val clazz = name.toClassOrNull() ?: continue
            clazz.allMethods { _, method ->
                method.hook().replaceTo(null)
            }

            clazz.allConstructors { _, constructor ->
                constructor.hook().replaceTo(null)
            }
        }
    }
}
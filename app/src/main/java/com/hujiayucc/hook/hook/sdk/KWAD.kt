package com.hujiayucc.hook.hook.sdk

import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.factory.allConstructors
import com.highcapable.yukihookapi.hook.factory.allMethods
import com.highcapable.yukihookapi.hook.factory.method

/** 快手广告 */
object KWAD : YukiBaseHooker() {

    private val nullReplaceList = arrayOf(
        "com.kwad.sdk.core.network.BaseResultData"
    )
    override fun onHook() {
        for (name in nullReplaceList) {
            val clazz = name.toClassOrNull() ?: continue
            clazz.allMethods { _, method ->
                method.hook().replaceTo(null)
            }

            clazz.allConstructors { _, constructor ->
                constructor.hook().replaceTo(null)
            }
        }

        "com.kwad.components.offline.api.core.network.model.CommonOfflineCompoResultData".toClassOrNull()?.method {
            name = "isResultOk"
        }?.ignored()?.hook()?.replaceToFalse()
    }
}
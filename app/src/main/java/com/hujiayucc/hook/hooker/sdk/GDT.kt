package com.hujiayucc.hook.hooker.sdk

import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker

/** 广电通 */
object GDT: YukiBaseHooker() {
    override fun onHook() {
        $$"com.qq.e.comm.managers.plugin.PM$a".toClassOrNull()
            ?.resolve()?.firstMethod { name = "call" }
            ?.hook { replaceTo(null) }

        $$"com.qq.e.comm.managers.plugin.PM$a".toClassOrNull()
            ?.resolve()?.firstMethod { name = "a" }
            ?.hook { replaceToFalse() }
    }
}
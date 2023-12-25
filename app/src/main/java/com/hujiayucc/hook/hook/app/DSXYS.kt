package com.hujiayucc.hook.hook.app

import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.factory.method

/** 大师兄影视 */
object DSXYS : YukiBaseHooker() {
    override fun onHook() {
        "com.tb.tb_lib.g.f".toClass().method {
            name = "biddingLoad"
        }.hook().replaceUnit {}

        "com.tb.tb_lib.g.d".toClass().method {
            name = "load"
        }.hook().replaceUnit {}

    }
}
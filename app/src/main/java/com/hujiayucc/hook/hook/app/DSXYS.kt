package com.hujiayucc.hook.hook.app

import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker

/** 大师兄影视 */
object DSXYS : YukiBaseHooker() {
    override fun onHook() {
        findClass("com.tb.tb_lib.g.f").hook {
            injectMember {
                method { name = "biddingLoad" }
                replaceUnit {}
            }
        }

        findClass("com.tb.tb_lib.g.d").hook {
            injectMember {
                method { name = "load" }
                replaceUnit {}
            }
        }
    }
}
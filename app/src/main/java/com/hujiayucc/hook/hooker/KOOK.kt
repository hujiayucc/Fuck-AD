package com.hujiayucc.hook.hooker

import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.log.YLog
import org.luckypray.dexkit.DexKitBridge

object KOOK : YukiBaseHooker() {
    override fun onHook() {
        YLog.debug("KOOK => 开始Hook")
        DexKitBridge.create(appClassLoader!!, true).use { bridge ->
            bridge.findClass {
                matcher {
                    methods {
                        add { name = "onClick" }
                    }
                }
            }.forEach { classData ->
                YLog.debug("Class: ${classData.name}")
            }
            bridge.close()
        }
    }
}
package com.hujiayucc.hook.hooker

import android.util.Log
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.factory.allMethods
import com.highcapable.yukihookapi.hook.log.YLog
import org.luckypray.dexkit.DexKitBridge

object Sdks : YukiBaseHooker() {
    override fun onHook() {
        YLog.debug("无差别禁用广告SDK")
        DexKitBridge.create(appClassLoader!!, true).use { bridge ->
            bridge.findClass {
                searchPackages("com.bytedance.sdk.openadsdk")
            }.forEach { classData ->
                classData.name.toClass()
                    .allMethods { _, method ->
                        val name = method.name.lowercase()
                        if (name.contains("init") || name.contains("show"))
                            method.hook {
                                before {
                                    val stack = Thread.currentThread().stackTrace
                                    for (i in stack.indices) {
                                        Log.d(
                                            "Stack", (i.toString() + ": " + stack[i].className
                                                    + "." + stack[i].methodName
                                                    + " (" + stack[i].fileName + ":" + stack[i].lineNumber + ")")
                                        )
                                    }
                                }
                            }
                    }
            }
        }
    }
}
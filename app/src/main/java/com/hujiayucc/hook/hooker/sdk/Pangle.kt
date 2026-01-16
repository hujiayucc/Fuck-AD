package com.hujiayucc.hook.hooker.sdk

import android.view.View
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import java.lang.Boolean

/** 穿山甲 */
object Pangle : YukiBaseHooker() {
    override fun onHook() {
        "com.bytedance.sdk.openadsdk.TTAdSdk".toClassOrNull()
            ?.resolve()?.method()?.build()?.forEach { method ->
                method.hook {
                    before {
                        result = when (method.self.returnType) {
                            Boolean.TYPE -> false
                            else -> null
                        }
                    }
                }
            }

        "com.bytedance.sdk.openadsdk.api.ln".toClassOrNull()
            ?.resolve()?.method()?.build()?.forEach { method ->
                method.hook {
                    before {
                        result = when (method.self.returnType) {
                            Boolean.TYPE -> false
                            else -> null
                        }
                    }
                }
            }

        "com.bytedance.sdk.openadsdk.core.AdSdkInitializerHolder".toClassOrNull()
            ?.resolve()?.method()?.build()?.forEach { method ->
                method.hook {
                    before {
                        result = when (method.self.returnType) {
                            Boolean.TYPE -> false
                            else -> null
                        }
                    }
                }
            }

        "com.bytedance.sdk.openadsdk.CSJConfig".toClassOrNull()
            ?.resolve()?.method()?.build()?.forEach { method ->
                method.hook {
                    before {
                        result = when (method.self.returnType) {
                            Boolean.TYPE -> false
                            else -> null
                        }
                    }
                }
            }

        $$"com.bytedance.sdk.openadsdk.AdSlot$Builder".toClassOrNull()
            ?.resolve()?.method()?.build()?.forEach { method ->
                method.hook {
                    before {
                        result = when (method.self.returnType) {
                            Boolean.TYPE -> false
                            else -> null
                        }
                    }
                }
            }

        "com.bytedance.sdk.openadsdk.core.component.splash.countdown.TTCountdownViewForCircle".toClassOrNull()
            ?.resolve()?.method()?.build()?.forEach { method ->
                method.hook {
                    after {
                        val view = instance as View
                        view.performClick()
                    }
                }
            }

        "com.bytedance.sdk.openadsdk.core.component.splash.e.r$1".toClassOrNull()
            ?.resolve()?.firstMethod { name = "run" }
            ?.hook { replaceUnit {} }
    }
}
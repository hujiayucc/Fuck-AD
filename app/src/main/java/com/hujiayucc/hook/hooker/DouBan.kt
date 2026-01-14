package com.hujiayucc.hook.hooker

import android.view.View
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.hujiayucc.hook.annotation.Run

@Run(
    appName = "豆瓣",
    packageName = "com.douban.frodo",
    action = "开屏广告",
    versions = [
        "7.105.1"
    ]
)
object DouBan : Base() {
    override fun onStart() {
        "com.bytedance.sdk.openadsdk.TTAdSdk".toClassOrNull()
            ?.resolve()?.method()?.build()?.forEach { method ->
                method.hook {
                    before {
                        result = when (method.self.returnType) {
                            java.lang.Boolean.TYPE -> false
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
                            java.lang.Boolean.TYPE -> false
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
                            java.lang.Boolean.TYPE -> false
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
                            java.lang.Boolean.TYPE -> false
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
                            java.lang.Boolean.TYPE -> false
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

        $$"com.qq.e.comm.managers.plugin.PM$a".toClassOrNull()
            ?.resolve()?.firstMethod { name = "call" }
            ?.hook { replaceTo(null) }

        $$"com.qq.e.comm.managers.plugin.PM$a".toClassOrNull()
            ?.resolve()?.firstMethod { name = "a" }
            ?.hook { replaceToFalse() }

        "com.douban.ad.h0".toClassOrNull()
            ?.resolve()?.firstMethod { name = "run" }
            ?.hook { replaceUnit {} }

        "com.douban.ad.g0".toClassOrNull()
            ?.resolve()?.firstMethod { name = "run" }
            ?.hook { replaceUnit {} }

        "com.douban.ad.k0".toClassOrNull()
            ?.resolve()?.firstMethod { name = "d" }
            ?.hook { replaceUnit {} }

        "com.douban.ad.AdView".toClassOrNull()
            ?.resolve()?.firstMethod { name = "b" }
            ?.hook { replaceUnit {} }

        "com.douban.ad.t".toClassOrNull()
            ?.resolve()?.firstMethod { name = "onGlobalLayout" }
            ?.hook { replaceUnit {} }

        $$"com.douban.frodo.splash.SplashAdNewRequestor$c".toClassOrNull()
            ?.resolve()?.firstMethod { name = "handleMessage" }
            ?.hook { replaceUnit {} }

        "com.bytedance.sdk.openadsdk.core.component.splash.e.r$1".toClassOrNull()
            ?.resolve()?.firstMethod { name = "run" }
            ?.hook { replaceUnit {} }
    }
}
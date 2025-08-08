package com.hujiayucc.hook.hooker

import android.view.View
import com.highcapable.yukihookapi.hook.factory.allMethods
import com.highcapable.yukihookapi.hook.factory.method
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
            ?.allMethods { _, method ->
                method.hook {
                    before {
                        result = when (method.returnType) {
                            java.lang.Boolean.TYPE -> false
                            else -> null
                        }
                    }
                }
            }

        "com.bytedance.sdk.openadsdk.api.ln".toClassOrNull()
            ?.allMethods { _, method ->
                method.hook {
                    before {
                        result = when (method.returnType) {
                            java.lang.Boolean.TYPE -> false
                            else -> null
                        }
                    }
                }
            }

        "com.bytedance.sdk.openadsdk.core.AdSdkInitializerHolder".toClassOrNull()
            ?.allMethods { _, method ->
                method.hook {
                    before {
                        result = when (method.returnType) {
                            java.lang.Boolean.TYPE -> false
                            else -> null
                        }
                    }
                }
            }

        "com.bytedance.sdk.openadsdk.CSJConfig".toClassOrNull()
            ?.allMethods { _, method ->
                method.hook {
                    before {
                        result = when (method.returnType) {
                            java.lang.Boolean.TYPE -> false
                            else -> null
                        }
                    }
                }
            }

        "com.bytedance.sdk.openadsdk.AdSlot\$Builder".toClassOrNull()
            ?.allMethods { _, method ->
                method.hook {
                    before {
                        result = when (method.returnType) {
                            java.lang.Boolean.TYPE -> false
                            else -> null
                        }
                    }
                }
            }

        "com.bytedance.sdk.openadsdk.core.component.splash.countdown.TTCountdownViewForCircle".toClassOrNull()
            ?.method { name = "getView" }
            ?.hook {
                after {
                    val view = instance as View
                    view.performClick()
                }
            }

        "com.qq.e.comm.managers.plugin.PM\$a".toClassOrNull()
            ?.method { name = "call" }
            ?.hook { replaceTo(null) }

        "com.qq.e.comm.managers.plugin.PM\$a".toClassOrNull()
            ?.method { name = "a" }
            ?.hook { replaceToFalse() }

        "com.douban.ad.h0".toClassOrNull()
            ?.method { name = "run" }
            ?.hook { replaceUnit {} }

        "com.douban.ad.g0".toClassOrNull()
            ?.method { name = "run" }
            ?.hook { replaceUnit {} }

        "com.douban.ad.k0".toClassOrNull()
            ?.method { name = "d" }
            ?.hook { replaceUnit {} }

        "com.douban.ad.AdView".toClassOrNull()
            ?.method { name = "b" }
            ?.hook { replaceUnit {} }

        "com.douban.ad.t".toClassOrNull()
            ?.method { name = "onGlobalLayout" }
            ?.hook { replaceUnit {} }

        "com.douban.frodo.splash.SplashAdNewRequestor\$c".toClassOrNull()
            ?.method { name = "handleMessage" }
            ?.hook { replaceUnit{} }

        "com.bytedance.sdk.openadsdk.core.component.splash.e.r$1".toClassOrNull()
            ?.method { name = "run" }
            ?.hook { replaceUnit {} }
    }
}
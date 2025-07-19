package com.hujiayucc.hook.hooker

import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.factory.constructor
import com.highcapable.yukihookapi.hook.factory.method
import com.highcapable.yukihookapi.hook.log.YLog
import de.robv.android.xposed.XposedHelpers
import org.luckypray.dexkit.DexKitBridge

object Sdks : YukiBaseHooker() {
    override fun onHook() {
        YLog.debug("无差别禁用广告SDK")
        "com.bytedance.sdk.openadsdk.TTAdSdk".toClassOrNull()
            ?.method { name = "init" }
            ?.hook { replaceToFalse() }

        "com.bytedance.sdk.openadsdk.api.plugin.f".toClassOrNull()
            ?.constructor()
            ?.hook { replaceTo(null) }

        "com.bytedance.sdk.openadsdk.AdSlot\$Builder".toClassOrNull()
            ?.method { name = "build" }
            ?.hook { replaceTo(null) }

        "com.qq.e.ads.splash.SplashAD".toClassOrNull()
            ?.constructor()
            ?.hook { before { XposedHelpers.callMethod(args[2], "onADClicked") } }

        "com.qq.e.ads.splash.SplashADListener".toClassOrNull()
            ?.constructor()
            ?.hook { replaceTo(null) }

        "com.qq.e.ads.nativ.NativeExpressAD".toClassOrNull()
            ?.method { name = "loadAD" }
            ?.hook { before { args[0] = 0 } }

        DexKitBridge.create(appClassLoader!!, true).use { bridge ->
            bridge.findClass {
                matcher {
                    methods {
                        add { name = "onADPresent" }
                        add { name = "onADDismissed" }
                        add { name = "onADClicked" }
                    }
                }
            }.forEach { classData ->
                if (classData.name == "com.qq.e.ads.splash.SplashADListener" ||
                    classData.name == "com.qq.e.tg.splash.TGSplashAdListener"
                ) return
                classData.name.toClassOrNull()
                    ?.method { name = "onADPresent" }
                    ?.hook { before { XposedHelpers.callMethod(instance, "onADClicked") } }
            }
        }
    }
}
package com.hujiayucc.hook.hooker.app

import com.hujiayucc.hook.annotation.RunJiaGu
import com.hujiayucc.hook.hooker.util.Hooker
import io.github.libxposed.api.XposedModuleInterface

@RunJiaGu(
    appName = "二柄",
    packageName = "com.diershoubing.erbing",
    action = "穿山甲广告"
)
object ErBing : Hooker() {
    override val jiaGuMarkerClasses = listOf(
        "com.diershoubing.erbing.model.utils.ad.TT.TTUtils",
        "com.bytedance.sdk.openadsdk.TTAdSdk",
        "com.bytedance.sdk.openadsdk.TTInitializer",
        "com.bytedance.sdk.openadsdk.TTAdNative",
        "com.bytedance.sdk.openadsdk.TTAdManager",
        "com.bytedance.sdk.openadsdk.CSJSplashAd",
        "com.bytedance.sdk.openadsdk.c.a.a\$a",
        "com.bytedance.sdk.openadsdk.api.a\$c",
        "com.bytedance.sdk.openadsdk.c.a.a.b",
        "com.bytedance.sdk.openadsdk.core.AdSdkInitializerHolder"
    )

    override fun XposedModuleInterface.PackageReadyParam.onPackageReady() {
        if (!isMainProcess(this)) return

        loadSdk(this, pangle = true)
        hookTTUtils()
    }

    private fun hookTTUtils() {
        "com.diershoubing.erbing.model.utils.ad.TT.TTUtils".toClassOrNull()
            ?.let { ttUtils ->
                ttUtils.methods("init").hook { replaceUnit {} }
                ttUtils.methods("loadSplash").hook { replaceUnit {} }
                ttUtils.methods("loadAd").hook { replaceUnit {} }
                ttUtils.methods("nativeAd").hook { replaceTo(null) }
                ttUtils.methods("shared").hook { replaceTo(null) }
                ttUtils.methods("isDisplaySplashAd").hook { replaceTo(false) }
                ttUtils.methods("rewardAd").forEach { method ->
                    if (method.returnType == Void.TYPE) {
                        method.hook { replaceUnit {} }
                    } else {
                        method.hook { replaceTo(null) }
                    }
                }
            }
    }
}

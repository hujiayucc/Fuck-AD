package com.hujiayucc.hook.hooker.app

import com.hujiayucc.hook.annotation.RunJiaGu
import com.hujiayucc.hook.hooker.util.Hooker
import io.github.libxposed.api.XposedModuleInterface

@RunJiaGu(
    appName = "疯狂刷题",
    packageName = "com.yaerxing.fkst",
    action = "开屏广告"
)
object FengKuangShuaTi : Hooker() {
    override val jiaGuMarkerClasses = listOf(
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
        loadSdk(this, pangle = true)
    }
}
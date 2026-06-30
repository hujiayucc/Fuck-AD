package com.hujiayucc.hook.hooker.app

import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.TextView
import com.hujiayucc.hook.annotation.RunJiaGu
import com.hujiayucc.hook.hooker.util.Hooker
import io.github.libxposed.api.XposedModuleInterface

@RunJiaGu(
    appName = "酷安",
    packageName = "com.coolapk.market",
    action = "禁用SDK, 信息流广告"
)
object CoolMarket : Hooker() {
    override val jiaGuMarkerClasses = listOf(
        "androidx.appcompat.widget.AppCompatImageView",
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
        "androidx.appcompat.widget.AppCompatImageView".toClassOrNull()
            ?.method("hasOverlappingRendering")
            ?.hook {
                after {
                    val view = instance<View>()
                    if (view.id == 0x7f0b0424) {
                        runMain { view.performClick() }
                    }
                }
            }

        View::class.java.methods("setOnClickListener")
            .hook {
                after {
                    val view = instance<View>()
                    if (view.id == 0x7f0b0370 || view.id == 0x7f0b0e37) {
                        runMain { view.performClick() }
                    } else if (view is TextView && view.text == "诱导点击") {
                        runMain { view.performClick() }
                    }
                }
            }
    }
}
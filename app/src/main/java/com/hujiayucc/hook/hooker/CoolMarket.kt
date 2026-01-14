package com.hujiayucc.hook.hooker

import android.view.View
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.hujiayucc.hook.annotation.RunJiaGu

@RunJiaGu(
    appName = "酷安",
    packageName = "com.coolapk.market",
    action = "禁用SDK, 信息流广告"
)
object CoolMarket : Base() {
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

        "androidx.appcompat.widget.AppCompatImageView".toClassOrNull()
            ?.resolve()?.firstMethod { name = "hasOverlappingRendering" }
            ?.hook {
                after {
                    val view = instance as View
                    if (view.id == 0x7f0b0424) {
                        if (view.isClickable && view.performClick()) {
                            debug("信息流广告")
                        }
                    }
                }
            }
    }
}
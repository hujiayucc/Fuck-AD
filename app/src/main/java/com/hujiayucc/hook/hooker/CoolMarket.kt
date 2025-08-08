package com.hujiayucc.hook.hooker

import android.view.View
import com.highcapable.yukihookapi.hook.factory.allMethods
import com.highcapable.yukihookapi.hook.factory.method
import com.hujiayucc.hook.annotation.RunJiaGu

@RunJiaGu(
    appName = "酷安",
    packageName = "com.coolapk.market",
    action = "禁用SDK, 信息流广告"
)
object CoolMarket : Base() {
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

        "androidx.appcompat.widget.AppCompatImageView".toClassOrNull()
            ?.method { name = "hasOverlappingRendering" }
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
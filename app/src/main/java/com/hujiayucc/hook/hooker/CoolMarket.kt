package com.hujiayucc.hook.hooker

import com.highcapable.yukihookapi.hook.factory.allMethods
import com.hujiayucc.hook.annotation.RunJiaGu

@RunJiaGu(
    "酷安",
    "com.coolapk.market",
    "禁用SDK"
)
object CoolMarket : BaseJiaGu() {
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
    }
}
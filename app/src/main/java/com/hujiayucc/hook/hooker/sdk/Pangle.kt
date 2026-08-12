package com.hujiayucc.hook.hooker.sdk

import android.view.View
import com.hujiayucc.hook.hooker.util.Hooker
import io.github.libxposed.api.XposedModuleInterface
import java.lang.reflect.Method

/** 穿山甲 */
object Pangle : Hooker() {
    private val booleanTypes = setOf(Boolean::class.javaPrimitiveType, Boolean::class.java)
    private val voidTypes = setOf(Void.TYPE, Void::class.java)

    private fun Method.replaceWithDefault() {
        hook {
            replace {
                when (returnType) {
                    in booleanTypes -> false
                    in voidTypes -> null
                    else -> null
                }
            }
        }
    }

    private fun Class<*>.methodOrNull(name: String, descriptor: String): Method? {
        return cachedDeclaredMethods().firstOrNull { method ->
            method.name == name && method.toDescriptor() == descriptor
        }
    }

    private fun Method.toDescriptor(): String {
        return parameterTypes.joinToString(
            prefix = "(",
            postfix = ")${returnType.toDescriptor()}"
        ) { parameterType -> parameterType.toDescriptor() }
    }

    private fun Class<*>.toDescriptor(): String {
        if (isPrimitive) {
            return when (this) {
                Void.TYPE -> "V"
                Boolean::class.javaPrimitiveType -> "Z"
                Byte::class.javaPrimitiveType -> "B"
                Char::class.javaPrimitiveType -> "C"
                Short::class.javaPrimitiveType -> "S"
                Int::class.javaPrimitiveType -> "I"
                Long::class.javaPrimitiveType -> "J"
                Float::class.javaPrimitiveType -> "F"
                Double::class.javaPrimitiveType -> "D"
                else -> "V"
            }
        }
        if (isArray) return name.replace('.', '/')
        return "L${name.replace('.', '/')};"
    }

    private fun Class<*>.hookMethods(vararg names: String) {
        cachedDeclaredMethods()
            .filter { method -> method.name in names }
            .forEach { method -> method.replaceWithDefault() }
    }

    private fun hookLegacySdkGuards() {
        "com.bytedance.sdk.openadsdk.api.ln".toClassOrNull()
            ?.hookMethods("init", "start", "isInitSuccess", "isSdkReady")

        "com.bytedance.sdk.openadsdk.core.AdSdkInitializerHolder".toClassOrNull()
            ?.hookMethods("init", "start", "isInitSuccess", "isSdkReady")

        "com.bytedance.sdk.openadsdk.core.component.splash.countdown.TTCountdownViewForCircle".toClassOrNull()
            ?.cachedDeclaredMethods()?.hook {
                after {
                    (chain.thisObject as? View)?.performClick()
                }
            }

        "com.bytedance.sdk.openadsdk.core.component.splash.e.r\$1".toClassOrNull()
            ?.methodOrNull("run")?.hook {
                replaceUnit {}
            }
    }

    private fun hookSdkInit() {
        "com.bytedance.sdk.openadsdk.TTAdSdk".toClassOrNull()
            ?.let { ttAdSdk ->
                ttAdSdk.methodOrNull("init", "(Landroid/content/Context;Lcom/bytedance/sdk/openadsdk/TTAdConfig;)Z")
                    ?.hook { replaceTo(false) }
                ttAdSdk.methodOrNull("start", "(Lcom/bytedance/sdk/openadsdk/TTAdSdk\$Callback;)V")
                    ?.hook { replaceUnit {} }
                ttAdSdk.methodOrNull("isInitSuccess", "()Z")
                    ?.hook { replaceTo(false) }
                ttAdSdk.methodOrNull("isSdkReady", "()Z")
                    ?.hook { replaceTo(false) }
                ttAdSdk.methodOrNull("updateAdConfig", "(Lcom/bytedance/sdk/openadsdk/TTAdConfig;)V")
                    ?.hook { replaceUnit {} }
                ttAdSdk.methodOrNull("updateConfigAuth", "(Lcom/bytedance/sdk/openadsdk/TTAdConfig;)V")
                    ?.hook { replaceUnit {} }
            }

        "com.bytedance.sdk.openadsdk.api.a".toClassOrNull()
            ?.let { initializer ->
                initializer.methodOrNull(
                    "a",
                    "(Landroid/content/Context;Lcom/bytedance/sdk/openadsdk/AdConfig;Lcom/bytedance/sdk/openadsdk/TTAdSdk\$InitCallback;)V"
                )?.hook { replaceUnit {} }
                initializer.methodOrNull(
                    "b",
                    "(Landroid/content/Context;Lcom/bytedance/sdk/openadsdk/AdConfig;Lcom/bytedance/sdk/openadsdk/TTAdSdk\$InitCallback;)Z"
                )?.hook { replaceTo(false) }
            }
    }

    private fun hookConfig() {
        "com.bytedance.sdk.openadsdk.CSJConfig".toClassOrNull()
            ?.let { config ->
                config.methodOrNull("isPaid")?.hook { replaceTo(false) }
                config.methodOrNull("isDebug")?.hook { replaceTo(false) }
                config.methodOrNull("isAllowShowNotify")?.hook { replaceTo(false) }
                config.methodOrNull("isSupportMultiProcess")?.hook { replaceTo(false) }
                config.methodOrNull("isUseMediation")?.hook { replaceTo(false) }
                config.methodOrNull("getPluginUpdateConfig")?.hook { replaceTo(0) }
                config.methodOrNull("getDirectDownloadNetworkType")?.hook { replaceTo(intArrayOf()) }
                config.methodOrNull("getMediationConfig")?.hook { replaceTo(null) }
            }
    }

    private fun hookAdSlot() {
        $$"com.bytedance.sdk.openadsdk.AdSlot$Builder".toClassOrNull()
            ?.let { builder ->
                listOf(
                    "setAdType",
                    "setCodeId",
                    "setAdCount",
                    "setSupportDeepLink",
                    "setPrimeRit",
                    "setRewardName",
                    "setRewardAmount",
                    "setMediationAdSlot"
                ).forEach { methodName ->
                    builder.methods(methodName).hook {
                        replace { instance }
                    }
                }
            }
    }

    private fun hookAdRequests() {
        "com.bytedance.sdk.openadsdk.c.a.a\$a".toClassOrNull()
            ?.let { adNative ->
                listOf(
                    "loadSplashAd",
                    "loadFeedAd",
                    "loadStream",
                    "loadDrawFeedAd",
                    "loadNativeAd",
                    "loadNativeExpressAd",
                    "loadExpressDrawFeedAd",
                    "loadBannerExpressAd",
                    "loadRewardVideoAd",
                    "loadFullScreenVideoAd"
                ).forEach { methodName ->
                    adNative.methods(methodName).hook { replaceUnit {} }
                }
            }

        "com.bytedance.sdk.openadsdk.api.a\$c".toClassOrNull()
            ?.let { manager ->
                manager.methodOrNull("createAdNative", "(Landroid/content/Context;)Lcom/bytedance/sdk/openadsdk/TTAdNative;")
                    ?.hook { replaceTo(null) }
                manager.methods("requestPermissionIfNecessary").hook { replaceUnit {} }
                manager.methods("tryShowInstallDialogWhenExit").hook { replaceTo(false) }
                manager.methods("getBiddingToken").hook { replaceTo(null) }
            }
    }

    private fun hookSplashAd() {
        "com.bytedance.sdk.openadsdk.c.a.a.b".toClassOrNull()
            ?.let { splashAd ->
                splashAd.methods("showSplashView").hook { replaceUnit {} }
                splashAd.methods("showSplashClickEyeView").hook { replaceUnit {} }
                splashAd.methods("showSplashCardView").hook { replaceUnit {} }
                splashAd.methods("startClickEye").hook { replaceUnit {} }
                splashAd.methods("hideSkipButton").hook { replaceUnit {} }
                splashAd.methods("getSplashView").hook { replaceTo(null) }
                splashAd.methods("getSplashClickEyeView").hook { replaceTo(null) }
                splashAd.methods("getSplashCardView").hook { replaceTo(null) }
            }
    }

    private fun hookAdObjects() {
        listOf(
            "com.bytedance.sdk.openadsdk.c.a.a.h",
            "com.bytedance.sdk.openadsdk.c.a.a.i",
            "com.bytedance.sdk.openadsdk.c.a.a.l"
        ).forEach { className ->
            className.toClassOrNull()
                ?.hookMethods(
                    "getAdView",
                    "registerViewForInteraction",
                    "render",
                    "showInteractionExpressAd",
                    "destroy"
                )
        }

        "com.bytedance.sdk.openadsdk.c.a.a.m".toClassOrNull()
            ?.hookMethods(
                "getExpressAdView",
                "render",
                "showInteractionExpressAd",
                "destroy"
            )

        "com.bytedance.sdk.openadsdk.c.a.a.j".toClassOrNull()
            ?.hookMethods("showFullScreenVideoAd")

        "com.bytedance.sdk.openadsdk.c.a.a.n".toClassOrNull()
            ?.hookMethods("showRewardVideoAd")
    }

    override fun XposedModuleInterface.PackageReadyParam.onPackageReady() {
        hookLegacySdkGuards()
        hookSdkInit()
        hookConfig()
        hookAdSlot()
        hookAdRequests()
        hookSplashAd()
        hookAdObjects()
    }
}
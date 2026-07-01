package com.hujiayucc.hook.hooker.sdk

import com.hujiayucc.hook.hooker.util.Hooker
import io.github.libxposed.api.XposedModuleInterface
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/** 广电通 */
object GDT : Hooker() {
    private val booleanTypes = setOf(Boolean::class.javaPrimitiveType, Boolean::class.java)
    private val voidTypes = setOf(Void.TYPE, Void::class.java)

    private fun Method.replaceWithDefault() {
        hook {
            replace {
                when (returnType) {
                    in booleanTypes -> false
                    in voidTypes -> null
                    String::class.java -> ""
                    Int::class.javaPrimitiveType, Int::class.java -> 0
                    Long::class.javaPrimitiveType, Long::class.java -> 0L
                    Float::class.javaPrimitiveType, Float::class.java -> 0F
                    Double::class.javaPrimitiveType, Double::class.java -> 0.0
                    Short::class.javaPrimitiveType, Short::class.java -> 0.toShort()
                    Byte::class.javaPrimitiveType, Byte::class.java -> 0.toByte()
                    Char::class.javaPrimitiveType, Char::class.java -> 0.toChar()
                    else -> null
                }
            }
        }
    }

    private fun Class<*>.hookMethods(vararg names: String) {
        declaredMethods
            .filter { method ->
                method.name in names &&
                    !Modifier.isAbstract(method.modifiers) &&
                    !Modifier.isNative(method.modifiers)
            }
            .forEach { method -> method.replaceWithDefault() }
    }

    private fun hookLegacyPluginGuard() {
        $$"com.qq.e.comm.managers.plugin.PM$a".toClassOrNull()
            ?.methodOrNull("a")
            ?.hook { replaceTo(false) }
    }

    private fun hookSdkInit() {
        "com.qq.e.comm.managers.GDTAdSdk".toClassOrNull()
            ?.hookMethods(
                "init",
                "initWithoutStart",
                "start"
            )

        "com.qq.e.comm.managers.status.SDKStatus".toClassOrNull()
            ?.hookMethods(
                "getSDKVersion",
                "getIntegrationSDKVersion",
                "getBuildInPluginVersion",
                "getPluginVersion"
            )
    }

    private fun hookSplashAd() {
        "com.qq.e.ads.splash.SplashAD".toClassOrNull()
            ?.hookMethods(
                "preLoad",
                "fetchAndShowIn",
                "fetchAdOnly",
                "showAd",
                "fetchFullScreenAndShowIn",
                "fetchFullScreenAdOnly",
                "showFullScreenAd",
                "isValid",
                "getAdNetWorkName",
                "getZoomOutBitmap"
            )
    }

    private fun hookRewardVideoAd() {
        "com.qq.e.ads.rewardvideo.RewardVideoAD".toClassOrNull()
            ?.hookMethods(
                "loadAD",
                "showAD",
                "hasShown",
                "isValid",
                "getVideoDuration",
                "getRewardAdType",
                "getAdNetWorkName"
            )
    }

    private fun hookInterstitialAd() {
        "com.qq.e.ads.interstitial2.UnifiedInterstitialAD".toClassOrNull()
            ?.hookMethods(
                "loadAD",
                "loadFullScreenAD",
                "show",
                "showFullScreenAD",
                "close",
                "destroy",
                "isValid",
                "getAdPatternType",
                "getVideoDuration",
                "getAdNetWorkName"
            )
    }

    private fun hookBannerAd() {
        "com.qq.e.ads.banner2.UnifiedBannerView".toClassOrNull()
            ?.hookMethods(
                "loadAD",
                "destroy",
                "setRefresh",
                "isValid",
                "getECPMLevel",
                "getECPM",
                "getAdNetWorkName",
                "getExtraInfo"
            )
    }

    private fun hookNativeAd() {
        "com.qq.e.ads.nativ.NativeExpressAD".toClassOrNull()
            ?.hookMethods(
                "loadAD",
                "setAdParams",
                "setVideoOption",
                "setMinVideoDuration",
                "setMaxVideoDuration",
                "getAdNetWorkName"
            )

        "com.qq.e.ads.nativ.NativeExpressADView".toClassOrNull()
            ?.hookMethods(
                "preloadVideo",
                "render",
                "negativeFeedback",
                "destroy",
                "setMediaListener"
            )

        "com.qq.e.ads.nativ.NativeUnifiedAD".toClassOrNull()
            ?.hookMethods(
                "loadData",
                "setMinVideoDuration",
                "setMaxVideoDuration",
                "setCategories",
                "getAdNetWorkName"
            )

        "com.qq.e.ads.nativ.NativeUnifiedADData".toClassOrNull()
            ?.hookMethods(
                "setNativeAdEventListener",
                "negativeFeedback",
                "bindAdToView",
                "bindAdToCustomVideo",
                "bindImageViews",
                "bindMediaView",
                "bindCTAViews",
                "resume",
                "destroy",
                "startVideo",
                "pauseVideo",
                "resumeVideo",
                "stopVideo",
                "setVideoMute",
                "pauseAppDownload",
                "resumeAppDownload"
            )
    }

    private fun hookHybridAd() {
        "com.qq.e.ads.hybrid.HybridAD".toClassOrNull()
            ?.hookMethods("loadUrl")
    }

    private fun hookPluginAdapters() {
        "com.qq.e.comm.plugin.splash.ANSplashAdViewAdapter".toClassOrNull()
            ?.hookMethods(
                "fetchAdOnly",
                "fetchFullScreenAdOnly",
                "showAd",
                "showFullScreenAd",
                "isValid"
            )

        "com.qq.e.comm.plugin.rewardvideo.ANRewardVideoAdAdapter".toClassOrNull()
            ?.hookMethods(
                "loadAD",
                "showAD",
                "hasShown",
                "isValid",
                "getVideoDuration"
            )

        "com.qq.e.comm.plugin.intersitial2.ANInterstitialAdAdapter".toClassOrNull()
            ?.hookMethods(
                "show",
                "showFullScreenAD",
                "close",
                "isValid",
                "getAdPatternType",
                "getVideoDuration"
            )

        "com.qq.e.comm.plugin.banner2.ANUnifiedBannerAdapter".toClassOrNull()
            ?.hookMethods(
                "loadAD",
                "destroy",
                "isValid"
            )

        "com.qq.e.comm.plugin.gdtnativead.ANNativeExpressAdAdapter".toClassOrNull()
            ?.hookMethods(
                "loadAD",
                "setVideoOption",
                "setMinVideoDuration",
                "setMaxVideoDuration"
            )

        "com.qq.e.comm.plugin.nativeadunified.ANNativeUnifiedAdAdapter".toClassOrNull()
            ?.hookMethods(
                "loadData",
                "setMinVideoDuration",
                "setMaxVideoDuration"
            )
    }

    private fun hookAdActivities() {
        listOf(
            "com.qq.e.ads.ADActivity",
            "com.qq.e.ads.PortraitADActivity",
            "com.qq.e.ads.LandscapeADActivity",
            "com.qq.e.ads.RewardvideoPortraitADActivity",
            "com.qq.e.ads.RewardvideoLandscapeADActivity",
            "com.qq.e.ads.DialogActivity"
        ).forEach { className ->
            className.toClassOrNull()
                ?.hookMethods("onCreate", "onStart", "onResume")
        }
    }

    override fun XposedModuleInterface.PackageReadyParam.onPackageReady() {
        hookLegacyPluginGuard()
        hookSdkInit()
        hookSplashAd()
        hookRewardVideoAd()
        hookInterstitialAd()
        hookBannerAd()
        hookNativeAd()
        hookHybridAd()
        hookPluginAdapters()
        hookAdActivities()
    }
}
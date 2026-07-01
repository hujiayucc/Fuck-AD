package com.hujiayucc.hook

import android.content.SharedPreferences
import android.util.Log
import com.hujiayucc.hook.data.SdkHookerConfig
import com.hujiayucc.hook.hooker.app.HookerRegistry
import com.hujiayucc.hook.hooker.sdk.AppLovin
import com.hujiayucc.hook.hooker.sdk.BaiQingTeng
import com.hujiayucc.hook.hooker.sdk.GDT
import com.hujiayucc.hook.hooker.sdk.GoogleAds
import com.hujiayucc.hook.hooker.sdk.KW
import com.hujiayucc.hook.hooker.sdk.LevelPlay
import com.hujiayucc.hook.hooker.sdk.Mintegral
import com.hujiayucc.hook.hooker.sdk.Pangle
import com.hujiayucc.hook.hooker.sdk.Sigmob
import com.hujiayucc.hook.hooker.sdk.TopOn
import com.hujiayucc.hook.hooker.sdk.TradPlus
import com.hujiayucc.hook.hooker.sdk.UnityAds
import com.hujiayucc.hook.hooker.sdk.Vungle
import com.hujiayucc.hook.hooker.util.ClickInfo
import com.hujiayucc.hook.hooker.util.Hooker
import com.hujiayucc.hook.hooker.util.Loader
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import java.util.concurrent.ConcurrentHashMap

class ModuleMain : XposedModule() {
    companion object {
        init {
            System.loadLibrary("dexkit")
        }

        private const val TAG = "ModuleMain"
        private const val PREFS_NAME = "config"
        private const val BASE_APK_SUFFIX = "/base.apk"
        val BUILTIN_HOOKERS = listOf(Loader, ClickInfo)
        val SDK_HOOKERS = listOf(
            GDT,
            KW,
            Pangle,
            BaiQingTeng,
            Sigmob,
            Mintegral,
            TopOn,
            TradPlus,
            GoogleAds,
            AppLovin,
            UnityAds,
            Vungle,
            LevelPlay
        )
        private val sdkHookerTargets = listOf(
            SdkHookerTarget(
                id = SdkHookerConfig.GDT,
                name = "GDT",
                hooker = GDT,
                markerClasses = listOf(
                    "com.qq.e.comm.managers.plugin.PM\$a",
                    "com.qq.e.comm.managers.GDTAdSdk",
                    "com.qq.e.comm.managers.status.SDKStatus",
                    "com.qq.e.ads.splash.SplashAD",
                    "com.qq.e.ads.rewardvideo.RewardVideoAD",
                    "com.qq.e.ads.interstitial2.UnifiedInterstitialAD",
                    "com.qq.e.ads.banner2.UnifiedBannerView",
                    "com.qq.e.ads.nativ.NativeExpressAD",
                    "com.qq.e.ads.nativ.NativeExpressADView",
                    "com.qq.e.ads.nativ.NativeUnifiedAD",
                    "com.qq.e.ads.nativ.NativeUnifiedADData",
                    "com.qq.e.ads.hybrid.HybridAD",
                    "com.qq.e.comm.plugin.splash.ANSplashAdViewAdapter",
                    "com.qq.e.comm.plugin.rewardvideo.ANRewardVideoAdAdapter",
                    "com.qq.e.comm.plugin.intersitial2.ANInterstitialAdAdapter",
                    "com.qq.e.comm.plugin.banner2.ANUnifiedBannerAdapter",
                    "com.qq.e.comm.plugin.gdtnativead.ANNativeExpressAdAdapter",
                    "com.qq.e.comm.plugin.nativeadunified.ANNativeUnifiedAdAdapter"
                )
            ),
            SdkHookerTarget(
                id = SdkHookerConfig.KW,
                name = "KW",
                hooker = KW,
                markerClasses = listOf(
                    "com.kwad.sdk.api.KsAdSDK",
                    "com.kwad.sdk.api.KsLoadManager",
                    "com.kwad.sdk.api.KsSplashScreenAd",
                    "com.kwad.sdk.api.KsRewardVideoAd",
                    "com.kwad.sdk.api.KsFullScreenVideoAd",
                    "com.kwad.sdk.api.KsInterstitialAd",
                    "com.kwad.sdk.api.KsFeedAd",
                    "com.kwad.sdk.api.KsDrawAd",
                    "com.kwad.sdk.api.KsNativeAd",
                    "com.duowan.kiwi.adsplash.view.AdSplashFragment",
                    "com.kwad.components.ad.splashscreen.widget.CircleSkipView"
                )
            ),
            SdkHookerTarget(
                id = SdkHookerConfig.PANGLE,
                name = "Pangle",
                hooker = Pangle,
                markerClasses = listOf(
                    "com.bytedance.sdk.openadsdk.TTAdSdk",
                    "com.bytedance.sdk.openadsdk.TTInitializer",
                    "com.bytedance.sdk.openadsdk.TTAdNative",
                    "com.bytedance.sdk.openadsdk.TTAdManager",
                    "com.bytedance.sdk.openadsdk.CSJSplashAd",
                    "com.bytedance.sdk.openadsdk.c.a.a\$a",
                    "com.bytedance.sdk.openadsdk.c.a.a.b",
                    "com.bytedance.sdk.openadsdk.c.a.a.h",
                    "com.bytedance.sdk.openadsdk.c.a.a.i",
                    "com.bytedance.sdk.openadsdk.c.a.a.j",
                    "com.bytedance.sdk.openadsdk.c.a.a.l",
                    "com.bytedance.sdk.openadsdk.c.a.a.m",
                    "com.bytedance.sdk.openadsdk.c.a.a.n",
                    "com.bytedance.sdk.openadsdk.api.a\$c",
                    "com.bytedance.sdk.openadsdk.CSJConfig",
                    "com.bytedance.sdk.openadsdk.AdSlot\$Builder",
                    "com.bytedance.sdk.openadsdk.api.ln",
                    "com.bytedance.sdk.openadsdk.core.AdSdkInitializerHolder",
                    "com.bytedance.sdk.openadsdk.core.component.splash.countdown.TTCountdownViewForCircle",
                    "com.bytedance.sdk.openadsdk.core.component.splash.e.r\$1"
                )
            ),
            SdkHookerTarget(
                id = SdkHookerConfig.BAIDU,
                name = "Baidu",
                hooker = BaiQingTeng,
                markerClasses = listOf(
                    "com.baidu.mobads.sdk.api.BDAdConfig",
                    "com.baidu.mobads.sdk.api.MobadsPermissionSettings",
                    "com.baidu.mobads.sdk.api.SplashAd",
                    "com.baidu.mobads.sdk.api.RewardVideoAd",
                    "com.baidu.mobads.sdk.api.InterstitialAd",
                    "com.baidu.mobads.sdk.api.ExpressInterstitialAd",
                    "com.baidu.mobads.sdk.api.BannerView",
                    "com.baidu.mobads.sdk.api.FeedNative",
                    "com.baidu.mobads.sdk.api.BaiduNativeManager",
                    "com.baidu.mobads.sdk.api.BaiduNative",
                    "com.baidu.mobads.sdk.api.XAdNativeResponse",
                    "com.baidu.mobads.sdk.api.NativeResponse",
                    "com.baidu.mobads.sdk.api.NativeCPUAdManager",
                    "com.baidu.mobads.sdk.api.AppActivity",
                    "com.baidu.mobads.sdk.api.MobRewardVideoActivity",
                    "com.baidu.mobads.sdk.api.MobadsVideoActivity"
                )
            ),
            SdkHookerTarget(
                id = SdkHookerConfig.SIGMOB,
                name = "Sigmob",
                hooker = Sigmob,
                markerClasses = listOf(
                    "com.sigmob.windad.WindAds",
                    "com.sigmob.windad.Splash.WindSplashAD",
                    "com.sigmob.windad.rewardVideo.WindRewardVideoAd",
                    "com.sigmob.windad.newInterstitial.WindNewInterstitialAd",
                    "com.sigmob.windad.natives.WindNativeUnifiedAd",
                    "com.sigmob.windad.natives.WindNativeAdData",
                    "com.sigmob.windad.WindNativeAd",
                    "com.sigmob.windad.WindBannerAd",
                    "com.sigmob.sdk.base.common.AdActivity",
                    "com.sigmob.sdk.base.common.TransparentAdActivity",
                    "com.sigmob.sdk.base.common.LandscapeAdActivity",
                    "com.sigmob.sdk.base.common.PortraitAdActivity"
                )
            ),
            SdkHookerTarget(
                id = SdkHookerConfig.MINTEGRAL,
                name = "Mintegral",
                hooker = Mintegral,
                markerClasses = listOf(
                    "com.mbridge.msdk.MBridgeSDK",
                    "com.mbridge.msdk.system.MBridgeSDKImpl",
                    "com.mbridge.msdk.out.MBridgeSDKFactory",
                    "com.mbridge.msdk.out.MBBannerView",
                    "com.mbridge.msdk.interstitialvideo.out.MBInterstitialVideoHandler",
                    "com.mbridge.msdk.out.MBInterstitialHandler",
                    "com.mbridge.msdk.out.MBRewardVideoHandler",
                    "com.mbridge.msdk.out.MBNativeHandler",
                    "com.mbridge.msdk.out.MBNativeAdvancedHandler",
                    "com.mbridge.msdk.out.MBSplashHandler"
                )
            ),
            SdkHookerTarget(
                id = SdkHookerConfig.TOPON,
                name = "TopOn",
                hooker = TopOn,
                markerClasses = listOf(
                    "com.anythink.core.api.ATSDK",
                    "com.anythink.core.common.base.AnyThinkBaseAdapter",
                    "com.anythink.splashad.api.ATSplashAd",
                    "com.anythink.splashad.bussiness.AdLoadManager",
                    "com.anythink.rewardvideo.api.ATRewardVideoAd",
                    "com.anythink.rewardvideo.bussiness.AdLoadManager",
                    "com.anythink.interstitial.api.ATInterstitial",
                    "com.anythink.interstitial.business.AdLoadManager",
                    "com.anythink.banner.api.ATBannerView",
                    "com.anythink.banner.business.AdLoadManager",
                    "com.anythink.nativead.api.ATNative",
                    "com.anythink.nativead.api.NativeAd",
                    "com.anythink.nativead.bussiness.AdLoadManager",
                    "com.anythink.nativead.banner.api.ATNativeBannerView",
                    "com.anythink.nativead.splash.api.ATNativeSplash",
                    "com.anythink.china.activity.TransparentActivity",
                    "com.anythink.myoffer.ui.MyOfferAdActivity"
                )
            ),
            SdkHookerTarget(
                id = SdkHookerConfig.TRADPLUS,
                name = "TradPlus",
                hooker = TradPlus,
                markerClasses = listOf(
                    "com.tradplus.ads.base.TradPlus",
                    "com.tradplus.ads.base.OpenLoadManager",
                    "com.tradplus.ads.base.adapter.TPBaseAdapter",
                    "com.tradplus.ads.base.adapter.reward.TPRewardAdapter",
                    "com.tradplus.ads.base.adapter.interstitial.TPInterstitialAdapter",
                    "com.tradplus.ads.base.adapter.banner.TPBannerAdapter",
                    "com.tradplus.ads.base.adapter.splash.TPSplashAdapter",
                    "com.tradplus.ads.base.adapter.nativead.TPNativeAdapter",
                    "com.tradplus.ads.open.TradPlusSdk",
                    "com.tradplus.ads.open.splash.TPSplash",
                    "com.tradplus.ads.open.reward.TPReward",
                    "com.tradplus.ads.open.interstitial.TPInterstitial",
                    "com.tradplus.ads.open.banner.TPBanner",
                    "com.tradplus.ads.open.nativead.TPNative"
                )
            ),
            SdkHookerTarget(
                id = SdkHookerConfig.GOOGLE,
                name = "Google Ads",
                hooker = GoogleAds,
                markerClasses = listOf(
                    "com.google.android.gms.ads.MobileAds",
                    "com.google.android.gms.ads.MobileAdsInitProvider",
                    "com.google.android.gms.ads.AdView",
                    "com.google.android.gms.ads.BaseAdView",
                    "com.google.android.gms.ads.AdLoader",
                    "com.google.android.gms.ads.interstitial.InterstitialAd",
                    "com.google.android.gms.ads.rewarded.RewardedAd",
                    "com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAd",
                    "com.google.android.gms.ads.appopen.AppOpenAd",
                    "com.google.android.gms.ads.admanager.AdManagerAdView",
                    "com.google.android.gms.ads.admanager.AdManagerInterstitialAd",
                    "com.google.android.gms.ads.nativead.NativeAd",
                    "com.google.android.gms.ads.nativead.NativeAdView",
                    "com.google.android.gms.ads.AdActivity",
                    "com.google.android.gms.ads.internal.overlay.AdOverlayInfoParcel"
                )
            ),
            SdkHookerTarget(
                id = SdkHookerConfig.APPLOVIN,
                name = "AppLovin MAX",
                hooker = AppLovin,
                markerClasses = listOf(
                    "com.applovin.sdk.AppLovinSdk",
                    "com.applovin.mediation.ads.MaxAdView",
                    "com.applovin.mediation.ads.MaxInterstitialAd",
                    "com.applovin.mediation.ads.MaxRewardedAd",
                    "com.applovin.mediation.ads.MaxAppOpenAd",
                    "com.applovin.mediation.nativeAds.MaxNativeAdLoader",
                    "com.applovin.mediation.nativeAds.MaxNativeAdView"
                )
            ),
            SdkHookerTarget(
                id = SdkHookerConfig.UNITY,
                name = "Unity Ads",
                hooker = UnityAds,
                markerClasses = listOf(
                    "com.unity3d.ads.UnityAds",
                    "com.unity3d.services.banners.BannerView",
                    "com.unity3d.services.ads.adunit.AdUnitActivity",
                    "com.unity3d.services.ads.adunit.AdUnitTransparentActivity",
                    "com.unity3d.services.ads.adunit.AdUnitTransparentSoftwareActivity",
                    "com.unity3d.services.ads.adunit.AdUnitSoftwareActivity"
                )
            ),
            SdkHookerTarget(
                id = SdkHookerConfig.VUNGLE,
                name = "Vungle/Liftoff",
                hooker = Vungle,
                markerClasses = listOf(
                    "com.vungle.ads.VungleAds",
                    "com.vungle.ads.BaseAd",
                    "com.vungle.ads.BaseFullscreenAd",
                    "com.vungle.ads.InterstitialAd",
                    "com.vungle.ads.RewardedAd",
                    "com.vungle.ads.BannerAd",
                    "com.vungle.ads.NativeAd",
                    "com.vungle.ads.VungleBannerView",
                    "com.vungle.ads.internal.ui.FullscreenAdActivity"
                )
            ),
            SdkHookerTarget(
                id = SdkHookerConfig.LEVELPLAY,
                name = "ironSource/LevelPlay",
                hooker = LevelPlay,
                markerClasses = listOf(
                    "com.ironsource.mediationsdk.IronSource",
                    "com.ironsource.mediationsdk.demandOnly.ISDemandOnlyBannerLayout",
                    "com.ironsource.sdk.controller.ControllerActivity",
                    "com.ironsource.sdk.controller.InterstitialActivity",
                    "com.ironsource.sdk.controller.OpenUrlActivity"
                )
            )
        )
        private val sdkHookerTargetCache = ConcurrentHashMap<String, List<SdkHookerTarget>>()

        lateinit var prefs: SharedPreferences
            private set
        lateinit var module: XposedModule
            private set
    }

    override fun onModuleLoaded(param: XposedModuleInterface.ModuleLoadedParam) {
        try {
            module = this
            prefs = getRemotePreferences(PREFS_NAME)
        } catch (e: Exception) {
            logIfDebug("onModuleLoaded", e)
        }
    }

    override fun onPackageReady(param: XposedModuleInterface.PackageReadyParam) {
        try {
            if (!param.isBaseApk()) {
                logIfDebug("Skip non-base APK: ${param.packageName} -> ${param.applicationInfo.sourceDir}")
                return
            }
            val appHookers = HookerRegistry.create(param.packageName)
            (BUILTIN_HOOKERS + appHookers).forEach { it.call(param) }
            if (appHookers.isEmpty()) {
                resolveSdkHookerTargets(param.packageName, param.classLoader).forEach { target ->
                    if (SdkHookerConfig.isEnabled(prefs, param.packageName, target.id)) {
                        target.hooker.call(param)
                    } else {
                        logIfDebug("Skip SDK hooker disabled: ${param.packageName} -> ${target.name}")
                    }
                }
            }
        } catch (e: Exception) {
            logIfDebug("onPackageReady", e)
        }
    }

    private fun XposedModuleInterface.PackageReadyParam.isBaseApk(): Boolean {
        return applicationInfo.sourceDir.endsWith(BASE_APK_SUFFIX)
    }

    private fun resolveSdkHookerTargets(
        packageName: String,
        classLoader: ClassLoader
    ): List<SdkHookerTarget> {
        sdkHookerTargetCache[packageName]?.let { cachedTargets ->
            logIfDebug("SDK fallback cache hit: $packageName -> ${cachedTargets.describe()}")
            return cachedTargets
        }
        val matchedTargets = sdkHookerTargets.filter { target ->
            target.markerClasses.any { className -> classLoader.hasClass(className) }
        }
        sdkHookerTargetCache[packageName] = matchedTargets
        logIfDebug("SDK fallback resolved: $packageName -> ${matchedTargets.describe()}")
        return matchedTargets
    }

    private fun List<SdkHookerTarget>.describe(): String {
        return if (isEmpty()) "none" else joinToString { it.name }
    }

    private fun ClassLoader.hasClass(className: String): Boolean {
        return runCatching { loadClass(className) }.isSuccess
    }

    private fun logIfDebug(message: String) {
        val shouldLog = runCatching { prefs.getBoolean("errorLog", false) }.getOrDefault(false)
        if (shouldLog) log(Log.DEBUG, TAG, message, null)
    }

    private fun logIfDebug(stage: String, error: Throwable) {
        val shouldLog = runCatching { prefs.getBoolean("errorLog", false) }.getOrDefault(false)
        if (shouldLog) log(Log.ERROR, TAG, "$stage error", error)
    }

    private data class SdkHookerTarget(
        val id: String,
        val name: String,
        val hooker: Hooker,
        val markerClasses: List<String>
    )
}

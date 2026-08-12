package com.hujiayucc.hook.hooker.sdk

import io.github.libxposed.api.XposedModuleInterface

/** TopOn / AnyThink */
object TopOn : SimpleSdkHooker() {
    override fun XposedModuleInterface.PackageReadyParam.onPackageReady() {
        hookClassMethods(
            "com.anythink.core.api.ATSDK",
            "init",
            "initCustomMap",
            "initPlacementCustomMap",
            "integrationChecking",
            "showGdprAuth",
            "setNetworkLogDebug",
            "setPersonalizedAdStatus",
            "setGDPRUploadDataLevel",
            "isCnSDK",
            "isNetworkLogDebug"
        )
        hookClassMethods(
            "com.anythink.splashad.api.ATSplashAd",
            "loadAd",
            "show",
            "showAd",
            "isAdReady",
            "isAdReadyToShow",
            "onDestory"
        )
        hookClassMethods(
            "com.anythink.rewardvideo.api.ATRewardVideoAd",
            "load",
            "show",
            "isAdReady",
            "checkAdStatus"
        )
        hookClassMethods(
            "com.anythink.interstitial.api.ATInterstitial",
            "load",
            "show",
            "isAdReady",
            "checkAdStatus"
        )
        hookClassMethods(
            "com.anythink.banner.api.ATBannerView",
            "loadAd",
            "destroy",
            "isAdReady"
        )
        hookClassMethods(
            "com.anythink.nativead.api.ATNative",
            "makeAdRequest",
            "checkAdStatus"
        )
        hookClassMethods(
            "com.anythink.nativead.api.NativeAd",
            "renderAdView",
            "clear"
        )
        hookClassMethods(
            "com.anythink.nativead.api.ATNativeAdView",
            "destroy"
        )
        hookClassMethods(
            "com.anythink.nativead.banner.api.ATNativeBannerView",
            "loadAd",
            "setVisibility"
        )
        hookClassMethods(
            "com.anythink.nativead.splash.api.ATNativeSplash",
            "onResume",
            "onPause",
            "onDestroy"
        )
        listOf(
            "com.anythink.splashad.bussiness.AdLoadManager",
            "com.anythink.rewardvideo.bussiness.AdLoadManager",
            "com.anythink.interstitial.business.AdLoadManager",
            "com.anythink.banner.business.AdLoadManager",
            "com.anythink.nativead.bussiness.AdLoadManager"
        ).forEach { managerClass ->
            hookClassMethods(managerClass, "startLoadAd", "show", "showNativeAd", "onAdLoaded", "onLoadError")
        }
        listOf(
            "com.anythink.splashad.bussiness.MediationGroupManager",
            "com.anythink.rewardvideo.bussiness.MediationGroupManager",
            "com.anythink.interstitial.business.MediationGroupManager",
            "com.anythink.banner.business.MediationGroupManager",
            "com.anythink.nativead.bussiness.MediationGroupManager"
        ).forEach { managerClass ->
            hookClassMethods(
                managerClass,
                "startLoadAd",
                "loadSplashAd",
                "loadRewardVideoAd",
                "loadInterstitialAd",
                "loadBannerAd",
                "loadNativeAd",
                "onDevelopLoaded",
                "onDeveloLoadFail"
            )
        }
        hookClassMethods(
            "com.anythink.core.common.base.AnyThinkBaseAdapter",
            "initNetworkObjectByPlacementId",
            "isAdReady"
        )
        listOf(
            "com.anythink.core.activity.AnyThinkGdprAuthActivity",
            "com.anythink.core.activity.ATAdActivity",
            "com.anythink.core.activity.ATLandscapeTranslucentActivity",
            "com.anythink.core.activity.ATPortraitTranslucentActivity"
        ).forEach { activityClass ->
            hookClassMethods(activityClass, "onCreate", "onStart", "onResume")
        }
    }
}
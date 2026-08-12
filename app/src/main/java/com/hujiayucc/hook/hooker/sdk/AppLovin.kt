package com.hujiayucc.hook.hooker.sdk

import io.github.libxposed.api.XposedModuleInterface

object AppLovin : SimpleSdkHooker() {
    private fun hookSdkInit() {
        hookClassMethods(
            "com.applovin.sdk.AppLovinSdk",
            "initialize",
            "reinitialize",
            "isInitialized",
            "showMediationDebugger",
            "showCreativeDebugger",
            "processDeepLink"
        )
    }

    private fun hookAdViews() {
        hookClassMethods(
            "com.applovin.mediation.ads.MaxAdView",
            "loadAd",
            "startAutoRefresh",
            "stopAutoRefresh",
            "destroy"
        )
    }

    private fun hookFullscreenAds() {
        listOf(
            "com.applovin.mediation.ads.MaxInterstitialAd",
            "com.applovin.mediation.ads.MaxRewardedAd",
            "com.applovin.mediation.ads.MaxAppOpenAd"
        ).forEach { className ->
            hookClassMethods(
                className,
                "loadAd",
                "showAd",
                "isReady",
                "isLoading",
                "isShowing",
                "destroy"
            )
        }
    }

    private fun hookNativeAds() {
        hookClassMethods(
            "com.applovin.mediation.nativeAds.MaxNativeAdLoader",
            "loadAd",
            "render",
            "destroy"
        )
        hookClassMethods(
            "com.applovin.mediation.nativeAds.MaxNativeAdView",
            "render",
            "recycle",
            "getMainView"
        )
    }

    override fun XposedModuleInterface.PackageReadyParam.onPackageReady() {
        hookSdkInit()
        hookAdViews()
        hookFullscreenAds()
        hookNativeAds()
    }
}

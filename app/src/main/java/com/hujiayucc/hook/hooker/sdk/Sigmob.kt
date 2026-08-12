package com.hujiayucc.hook.hooker.sdk

import io.github.libxposed.api.XposedModuleInterface

/** Sigmob */
object Sigmob : SimpleSdkHooker() {
    override fun XposedModuleInterface.PackageReadyParam.onPackageReady() {
        hookClassMethods(
            "com.sigmob.windad.WindAds",
            "init",
            "start",
            "startWithOptions",
            "requestPermission",
            "isInit",
            "setPersonalizedAdvertisingOn",
            "setUserGDPRConsentStatus",
            "setIsAgeRestrictedUser"
        )
        hookClassMethods(
            "com.sigmob.windad.Splash.WindSplashAD",
            "loadAd",
            "loadAndShow",
            "show",
            "destroy",
            "isReady"
        )
        hookClassMethods(
            "com.sigmob.windad.rewardVideo.WindRewardVideoAd",
            "loadAd",
            "show",
            "destroy",
            "isReady"
        )
        hookClassMethods(
            "com.sigmob.windad.newInterstitial.WindNewInterstitialAd",
            "loadAd",
            "show",
            "destroy",
            "isReady"
        )
        hookClassMethods(
            "com.sigmob.windad.interstitial.WindInterstitialAd",
            "loadAd",
            "show",
            "destroy",
            "isReady"
        )
        hookClassMethods(
            "com.sigmob.windad.WindNativeAd",
            "loadAd",
            "destroy"
        )
        hookClassMethods(
            "com.sigmob.windad.natives.WindNativeUnifiedAd",
            "loadAd",
            "destroy"
        )
        hookClassMethods(
            "com.sigmob.windad.natives.WindNativeAdData",
            "bindViewForInteraction",
            "bindMediaView",
            "bindMediaViewWithoutAppInfo",
            "startVideo",
            "pauseVideo",
            "resumeVideo",
            "stopVideo",
            "destroy"
        )
        hookClassMethods(
            "com.sigmob.windad.WindBannerAd",
            "loadAd",
            "destroy"
        )
        listOf(
            "com.sigmob.sdk.base.common.AdActivity",
            "com.sigmob.sdk.base.common.TransparentAdActivity",
            "com.sigmob.sdk.base.common.LandscapeAdActivity",
            "com.sigmob.sdk.base.common.LandscapeTransparentAdActivity",
            "com.sigmob.sdk.base.common.PortraitAdActivity",
            "com.sigmob.sdk.base.common.PortraitTransparentAdActivity",
            "com.sigmob.sdk.base.BaseAdActivity",
            "com.sigmob.sdk.base.common.SigmobBrowserActivity"
        ).forEach { activityClass ->
            hookClassMethods(activityClass, "onCreate", "onStart", "onResume")
        }
    }
}
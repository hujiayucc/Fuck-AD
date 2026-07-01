package com.hujiayucc.hook.hooker.sdk

import io.github.libxposed.api.XposedModuleInterface

object Vungle : SimpleSdkHooker() {
    private fun hookSdkApi() {
        hookClassMethods(
            "com.vungle.ads.VungleAds",
            "init",
            "isInitialized",
            "getBiddingToken",
            "isInline",
            "deInit"
        )
        hookClassMethods(
            "com.vungle.ads.VungleAds\$Companion",
            "init",
            "isInitialized",
            "getBiddingToken",
            "isInline",
            "deInit"
        )
    }

    private fun hookBaseAds() {
        hookClassMethods(
            "com.vungle.ads.BaseAd",
            "canPlayAd",
            "load",
            "sendWinURL",
            "sendLossURL"
        )
        hookClassMethods(
            "com.vungle.ads.BaseFullscreenAd",
            "load",
            "play"
        )
    }

    private fun hookBannerAds() {
        hookClassMethods(
            "com.vungle.ads.BannerAd",
            "finishAd",
            "getBannerView"
        )
        hookClassMethods(
            "com.vungle.ads.VungleBannerView",
            "load",
            "finishAd",
            "setAdVisibility"
        )
    }

    private fun hookNativeAds() {
        hookClassMethods(
            "com.vungle.ads.NativeAd",
            "unregisterView",
            "registerViewForInteraction",
            "performCTA",
            "hasCallToAction",
            "hasVideoContent"
        )
    }

    private fun hookAdActivities() {
        hookClassMethods(
            "com.vungle.ads.internal.ui.FullscreenAdActivity",
            "onCreate",
            "onStart",
            "onResume"
        )
    }

    override fun XposedModuleInterface.PackageReadyParam.onPackageReady() {
        hookSdkApi()
        hookBaseAds()
        hookBannerAds()
        hookNativeAds()
        hookAdActivities()
    }
}
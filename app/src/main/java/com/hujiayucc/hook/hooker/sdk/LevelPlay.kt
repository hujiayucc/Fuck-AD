package com.hujiayucc.hook.hooker.sdk

import io.github.libxposed.api.XposedModuleInterface

object LevelPlay : SimpleSdkHooker() {
    private fun hookIronSourceApi() {
        hookClassMethods(
            "com.ironsource.mediationsdk.IronSource",
            "setMediationType",
            "getISDemandOnlyBiddingData",
            "loadISDemandOnlyRewardedVideo",
            "showISDemandOnlyRewardedVideo",
            "isISDemandOnlyRewardedVideoAvailable",
            "loadISDemandOnlyInterstitial",
            "showISDemandOnlyInterstitial",
            "isISDemandOnlyInterstitialReady",
            "loadISDemandOnlyBanner",
            "createBannerForDemandOnly",
            "destroyISDemandOnlyBanner",
            "setAdRevenueData"
        )
    }

    private fun hookBannerLayout() {
        hookClassMethods(
            "com.ironsource.mediationsdk.demandOnly.ISDemandOnlyBannerLayout",
            "isDestroyed",
            "getBannerView",
            "setBannerDemandOnlyListener",
            "removeBannerListener"
        )
    }

    private fun hookAdActivities() {
        listOf(
            "com.ironsource.sdk.controller.ControllerActivity",
            "com.ironsource.sdk.controller.InterstitialActivity",
            "com.ironsource.sdk.controller.OpenUrlActivity"
        ).forEach { className ->
            hookClassMethods(className, "onCreate", "onStart", "onResume")
        }
    }

    override fun XposedModuleInterface.PackageReadyParam.onPackageReady() {
        hookIronSourceApi()
        hookBannerLayout()
        hookAdActivities()
    }
}
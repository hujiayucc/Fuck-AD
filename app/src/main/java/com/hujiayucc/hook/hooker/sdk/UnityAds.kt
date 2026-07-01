package com.hujiayucc.hook.hooker.sdk

import io.github.libxposed.api.XposedModuleInterface

object UnityAds : SimpleSdkHooker() {
    private fun hookSdkApi() {
        hookClassMethods(
            "com.unity3d.ads.UnityAds",
            "initialize",
            "isInitialized",
            "isSupported",
            "show",
            "load",
            "getToken"
        )
    }

    private fun hookBannerAds() {
        hookClassMethods(
            "com.unity3d.services.banners.BannerView",
            "load",
            "bridgeLoad",
            "destroy",
            "registerInitializeListener",
            "unregisterInitializeListener"
        )
    }

    private fun hookAdActivities() {
        listOf(
            "com.unity3d.services.ads.adunit.AdUnitActivity",
            "com.unity3d.services.ads.adunit.AdUnitTransparentActivity",
            "com.unity3d.services.ads.adunit.AdUnitTransparentSoftwareActivity",
            "com.unity3d.services.ads.adunit.AdUnitSoftwareActivity"
        ).forEach { className ->
            hookClassMethods(className, "onCreate", "onStart", "onResume")
        }
    }

    override fun XposedModuleInterface.PackageReadyParam.onPackageReady() {
        hookSdkApi()
        hookBannerAds()
        hookAdActivities()
    }
}
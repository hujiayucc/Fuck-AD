package com.hujiayucc.hook.hooker.sdk

import android.annotation.SuppressLint
import android.view.View
import com.hujiayucc.hook.hooker.util.Hooker
import io.github.libxposed.api.XposedModuleInterface
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.Collections

/** 快手 */
object KW : Hooker() {
    private val hookedLoadManagerClasses = Collections.synchronizedSet(mutableSetOf<String>())
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
        (declaredMethods.asSequence() + methods.asSequence())
            .filter { method ->
                method.name in names &&
                    !Modifier.isAbstract(method.modifiers) &&
                    !Modifier.isNative(method.modifiers)
            }
            .distinctBy { method -> method.toGenericString() }
            .forEach { method -> method.replaceWithDefault() }
    }

    private fun hookSdkInit() {
        "com.kwad.sdk.api.KsAdSDK".toClassOrNull()
            ?.let { sdk ->
                sdk.hookMethods(
                    "init",
                    "start",
                    "isInitSuccess",
                    "isSdkReady"
                )

                sdk.methods("getLoadManager").hook {
                    after {
                        result?.let { loadManager -> hookLoadManager(loadManager.javaClass) }
                    }
                }
            }
    }

    private fun hookLoadManager(loadManagerClass: Class<*>) {
        val loaderKey = System.identityHashCode(loadManagerClass.classLoader)
        if (!hookedLoadManagerClasses.add("${loadManagerClass.name}@$loaderKey")) return
        loadManagerClass.hookMethods(
            "loadFullScreenVideoAd",
            "loadRewardVideoAd",
            "loadFeedAd",
            "loadConfigFeedAd",
            "loadDrawAd",
            "loadNativeAd",
            "loadSplashScreenAd",
            "loadInterstitialAd",
            "loadInteractionAd"
        )
    }

    private fun hookAdObjects() {
        "com.kwad.sdk.api.KsRewardVideoAd".toClassOrNull()
            ?.hookMethods(
                "showRewardVideoAd",
                "setRewardAdInteractionListener",
                "setDownloadListener"
            )

        "com.kwad.sdk.api.KsFullScreenVideoAd".toClassOrNull()
            ?.hookMethods(
                "showFullScreenVideoAd",
                "setFullScreenVideoAdInteractionListener",
                "setDownloadListener"
            )

        "com.kwad.sdk.api.KsInterstitialAd".toClassOrNull()
            ?.hookMethods(
                "showInterstitialAd",
                "setAdInteractionListener",
                "setDownloadListener",
                "isAdEnable",
                "getECPM"
            )

        "com.kwad.sdk.api.KsSplashScreenAd".toClassOrNull()
            ?.hookMethods(
                "showSplashMiniWindow",
                "showSplashMiniWindowIfNeeded",
                "getView",
                "setSplashScreenAdInteractionListener"
            )

        "com.kwad.sdk.api.KsFeedAd".toClassOrNull()
            ?.hookMethods(
                "getFeedView",
                "setVideoSoundEnable",
                "setAdInteractionListener",
                "setDownloadListener"
            )

        "com.kwad.sdk.api.KsDrawAd".toClassOrNull()
            ?.hookMethods(
                "getDrawView",
                "setAdInteractionListener",
                "setVideoSoundEnable",
                "setDownloadListener"
            )

        "com.kwad.sdk.api.KsNativeAd".toClassOrNull()
            ?.hookMethods(
                "registerViewForInteraction",
                "setVideoPlayListener",
                "setDownloadListener",
                "isAdEnable",
                "getAdSource",
                "getAdDescription",
                "getAdView"
            )
    }

    @SuppressLint("ResourceType")
    private fun hookLegacySkipControls() {
        "com.duowan.kiwi.adsplash.view.AdSplashFragment".toClassOrNull()
            ?.methods("findViews")
            ?.hook {
                after {
                    val view = (chain.args[0] as View).findViewById<View>(0x7f0923c9)
                    runMain { view.performClick() }
                }
            }

        "com.kwad.components.ad.splashscreen.widget.CircleSkipView".toClassOrNull()
            ?.declaredMethods?.hook {
                after {
                    val view = instance<View>()
                    runMain { view.performClick() }
                }
            }
    }

    override fun XposedModuleInterface.PackageReadyParam.onPackageReady() {
        hookSdkInit()
        hookAdObjects()
        hookLegacySkipControls()
    }
}

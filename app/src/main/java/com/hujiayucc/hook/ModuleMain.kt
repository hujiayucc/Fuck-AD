package com.hujiayucc.hook

import android.content.SharedPreferences
import android.util.Log
import com.hujiayucc.hook.hooker.app.HookerRegistry
import com.hujiayucc.hook.hooker.sdk.GDT
import com.hujiayucc.hook.hooker.sdk.KW
import com.hujiayucc.hook.hooker.sdk.Pangle
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
        val SDK_HOOKERS = listOf(GDT, KW, Pangle)
        private val sdkHookerTargets = listOf(
            SdkHookerTarget(
                name = "GDT",
                hooker = GDT,
                markerClasses = listOf("com.qq.e.comm.managers.plugin.PM\$a")
            ),
            SdkHookerTarget(
                name = "KW",
                hooker = KW,
                markerClasses = listOf(
                    "com.duowan.kiwi.adsplash.view.AdSplashFragment",
                    "com.kwad.components.ad.splashscreen.widget.CircleSkipView"
                )
            ),
            SdkHookerTarget(
                name = "Pangle",
                hooker = Pangle,
                markerClasses = listOf(
                    "com.bytedance.sdk.openadsdk.TTAdSdk",
                    "com.bytedance.sdk.openadsdk.api.ln",
                    "com.bytedance.sdk.openadsdk.core.AdSdkInitializerHolder",
                    "com.bytedance.sdk.openadsdk.CSJConfig",
                    "com.bytedance.sdk.openadsdk.AdSlot\$Builder",
                    "com.bytedance.sdk.openadsdk.core.component.splash.countdown.TTCountdownViewForCircle",
                    "com.bytedance.sdk.openadsdk.core.component.splash.e.r\$1"
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
                    target.hooker.call(param)
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
        val name: String,
        val hooker: Hooker,
        val markerClasses: List<String>
    )
}

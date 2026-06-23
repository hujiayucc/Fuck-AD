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
                hooker = GDT,
                markerClasses = listOf("com.qq.e.comm.managers.plugin.PM\$a")
            ),
            SdkHookerTarget(
                hooker = KW,
                markerClasses = listOf(
                    "com.duowan.kiwi.adsplash.view.AdSplashFragment",
                    "com.kwad.components.ad.splashscreen.widget.CircleSkipView"
                )
            ),
            SdkHookerTarget(
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
            if (!param.isBaseApk()) return
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
        sdkHookerTargetCache[packageName]?.let { return it }
        val matchedTargets = sdkHookerTargets.filter { target ->
            target.markerClasses.any { className -> classLoader.hasClass(className) }
        }
        sdkHookerTargetCache[packageName] = matchedTargets
        return matchedTargets
    }

    private fun ClassLoader.hasClass(className: String): Boolean {
        return runCatching { loadClass(className) }.isSuccess
    }

    private fun logIfDebug(stage: String, error: Throwable) {
        val shouldLog = runCatching { prefs.getBoolean("errorLog", false) }.getOrDefault(false)
        if (shouldLog) log(Log.ERROR, TAG, "$stage error", error)
    }

    private data class SdkHookerTarget(
        val hooker: Hooker,
        val markerClasses: List<String>
    )
}

package com.hujiayucc.hook

import android.content.SharedPreferences
import android.util.Log
import com.hujiayucc.hook.data.FallbackSharedPreferences
import com.hujiayucc.hook.data.SdkHookerConfig
import com.hujiayucc.hook.hooker.app.HookerRegistry
import com.hujiayucc.hook.hooker.sdk.SdkHookerRegistry
import com.hujiayucc.hook.hooker.sdk.SdkHookerResolver
import com.hujiayucc.hook.hooker.util.ClickInfo
import com.hujiayucc.hook.hooker.util.Hooker
import com.hujiayucc.hook.hooker.util.Loader
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface

class ModuleMain : XposedModule() {
    companion object {
        private const val TAG = "ModuleMain"
        private const val PREFS_NAME = "config"

        val BUILTIN_HOOKERS = listOf(Loader)
        val SDK_HOOKERS: List<Hooker> = SdkHookerRegistry.hookers

        @Volatile
        private var dexKitLoadAttempted = false

        @Volatile
        private var dexKitLoaded = false

        var prefs: SharedPreferences = FallbackSharedPreferences
            private set
        lateinit var module: XposedModule
            private set

        fun ensureDexKitLoaded(): Boolean {
            if (dexKitLoaded) return true

            return synchronized(this) {
                if (dexKitLoaded) {
                    true
                } else if (dexKitLoadAttempted) {
                    false
                } else {
                    val loaded = runCatching {
                        System.loadLibrary("dexkit")
                    }.isSuccess
                    dexKitLoaded = loaded
                    dexKitLoadAttempted = true
                    loaded
                }
            }
        }
    }

    override fun onModuleLoaded(param: XposedModuleInterface.ModuleLoadedParam) {
        module = this
        val remotePreferences = runCatching {
            getRemotePreferences(PREFS_NAME)
        }.onFailure { error ->
            runCatching {
                log(Log.WARN, TAG, "Remote preferences unavailable; using fallback preferences", error)
            }
        }
        prefs = remotePreferences.getOrDefault(FallbackSharedPreferences)
        log(
            Log.INFO,
            TAG,
            "Module loaded: process=${param.processName}, framework=$frameworkName, " +
                "api=$apiVersion, remotePreferences=${remotePreferences.isSuccess}",
            null
        )
    }

    override fun onPackageReady(param: XposedModuleInterface.PackageReadyParam) {
        try {
            val appHookers = HookerRegistry.create(param.packageName)
            logIfDebug(
                "Package ready: package=${param.packageName}, appHookers=" +
                    appHookers.joinToString { it.javaClass.simpleName }.ifEmpty { "none" }
            )
            val hookers = if (ClickInfo.isEnabled()) {
                BUILTIN_HOOKERS + ClickInfo + appHookers
            } else {
                BUILTIN_HOOKERS + appHookers
            }
            hookers.forEach { it.call(param) }
            if (appHookers.isEmpty()) {
                resolveSdkHookerTargets(param.packageName, param.classLoader).forEach { match ->
                    val target = match.target
                    if (SdkHookerConfig.isEnabled(prefs, param.packageName, target.id)) {
                        target.hooker.call(param)
                    } else {
                        logIfDebug("Skip SDK hooker disabled: ${param.packageName} -> ${target.name}")
                    }
                }
            }
        } catch (error: LinkageError) {
            log(Log.ERROR, TAG, "onPackageReady linkage error: ${param.packageName}", error)
        } catch (error: Exception) {
            logIfDebug("onPackageReady", error)
        }
    }

    private fun resolveSdkHookerTargets(
        packageName: String,
        classLoader: ClassLoader
    ) = SdkHookerResolver.resolve(packageName, classLoader).also { targets ->
        logIfDebug("SDK fallback resolved: $packageName -> ${SdkHookerResolver.describe(targets)}")
    }

    private fun logIfDebug(message: String) {
        val shouldLog = runCatching { prefs.getBoolean("errorLog", false) }.getOrDefault(false)
        if (shouldLog) log(Log.DEBUG, TAG, message, null)
    }

    private fun logIfDebug(stage: String, error: Throwable) {
        val shouldLog = runCatching { prefs.getBoolean("errorLog", false) }.getOrDefault(false)
        if (shouldLog) log(Log.ERROR, TAG, "$stage error", error)
    }
}

package com.hujiayucc.hook

import android.content.SharedPreferences
import android.util.Log
import com.hujiayucc.hook.hooker.sdk.GDT
import com.hujiayucc.hook.hooker.sdk.KW
import com.hujiayucc.hook.hooker.sdk.Pangle
import com.hujiayucc.hook.hooker.util.ClickInfo
import com.hujiayucc.hook.hooker.util.Hooker
import com.hujiayucc.hook.hooker.util.Loader
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.query.enums.StringMatchType
import java.util.concurrent.ConcurrentHashMap

class ModuleMain : XposedModule() {
    companion object {
        init {
            System.loadLibrary("dexkit")
        }

        private const val TAG = "ModuleMain"
        private const val PREFS_NAME = "config"
        private const val BASE_APK_SUFFIX = "/base.apk"
        private const val HOOKER_PACKAGE = "com.hujiayucc.hook.hooker.app"
        val BUILTIN_HOOKERS = listOf(Loader, ClickInfo)
        val SDK_HOOKERS = listOf(GDT, KW, Pangle)
        private val appHookerClassCache = ConcurrentHashMap<String, List<Class<out Hooker>>>()

        lateinit var prefs: SharedPreferences
            private set
        lateinit var module: XposedModule
            private set
        lateinit var bridge: DexKitBridge
            private set
    }

    override fun onModuleLoaded(param: XposedModuleInterface.ModuleLoadedParam) {
        try {
            module = this
            prefs = getRemotePreferences(PREFS_NAME)
            bridge = DexKitBridge.create(javaClass.classLoader!!, true)
        } catch (e: Exception) {
            logIfDebug("onModuleLoaded", e)
        }
    }

    override fun onPackageLoaded(param: XposedModuleInterface.PackageLoadedParam) {
        try {
            if (!param.isBaseApk()) return
            resolveAppHookerClasses(param.packageName)
        } catch (e: Exception) {
            logIfDebug("onPackageLoaded", e)
        }
    }

    override fun onPackageReady(param: XposedModuleInterface.PackageReadyParam) {
        try {
            if (!param.isBaseApk()) return
            val appHookers = createAppHookers(param.packageName)
            (BUILTIN_HOOKERS + appHookers).forEach { it.call(param) }
            if (appHookers.isEmpty()) {
                SDK_HOOKERS.forEach { it.call(param) }
            }
        } catch (e: Exception) {
            logIfDebug("onPackageReady", e)
        }
    }

    private fun XposedModuleInterface.PackageLoadedParam.isBaseApk(): Boolean {
        return applicationInfo.sourceDir.endsWith(BASE_APK_SUFFIX)
    }

    private fun XposedModuleInterface.PackageReadyParam.isBaseApk(): Boolean {
        return applicationInfo.sourceDir.endsWith(BASE_APK_SUFFIX)
    }

    private fun createAppHookers(packageName: String): List<Hooker> {
        return resolveAppHookerClasses(packageName).mapNotNull { hookerClass ->
            try {
                hookerClass.getDeclaredConstructor().apply { isAccessible = true }.newInstance()
            } catch (e: Exception) {
                logIfDebug("createAppHooker:${hookerClass.name}", e)
                null
            }
        }
    }

    private fun resolveAppHookerClasses(packageName: String): List<Class<out Hooker>> {
        appHookerClassCache[packageName]?.let { return it }
        val moduleClassLoader = javaClass.classLoader ?: return emptyList()
        val hookerClasses = bridge.findClass {
            searchPackages(HOOKER_PACKAGE)
            matcher {
                annotations {
                    add {
                        addElement {
                            name = "packageName"
                            stringValue(packageName, StringMatchType.Equals)
                        }
                    }
                }
            }
        }.mapNotNull { data ->
            try {
                data.getInstance(moduleClassLoader).asSubclass(Hooker::class.java)
            } catch (e: Exception) {
                logIfDebug("resolveAppHookerClass:$packageName", e)
                null
            }
        }
        appHookerClassCache[packageName] = hookerClasses
        return hookerClasses
    }

    private fun logIfDebug(stage: String, error: Throwable) {
        val shouldLog = runCatching { prefs.getBoolean("errorLog", false) }.getOrDefault(false)
        if (shouldLog) log(Log.ERROR, TAG, "$stage error", error)
    }
}

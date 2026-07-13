package com.hujiayucc.hook.hooker.util

import android.annotation.SuppressLint
import android.app.Application
import android.app.Instrumentation
import android.content.Context
import android.content.ContextWrapper
import dalvik.system.BaseDexClassLoader
import io.github.libxposed.api.XposedModuleInterface

internal class JiaGuHookCoordinator(
    private val owner: Hooker,
    private val appName: () -> String,
    private val currentClassLoader: () -> ClassLoader?,
    private val updateClassLoader: (ClassLoader) -> Unit,
    private val markerClasses: () -> List<String>,
    private val retryDelays: () -> List<Long>,
    private val enableLoadClassProbe: () -> Boolean
) {
    fun run(param: XposedModuleInterface.PackageReadyParam) {
        if (markerClasses().isEmpty()) {
            HookerLogger.hookError(
                "Skip ${appName()} JiaGu hook because marker classes are empty",
                IllegalStateException("@RunJiaGu requires marker classes for real ClassLoader verification.")
            )
            return
        }

        val state = State(param)
        if (state.tryClassLoader(currentClassLoader(), "PackageReadyParam")) return
        state.installLifecycleHooks()
        state.installDexClassLoaderHooks()
        state.scheduleRetries()
        if (enableLoadClassProbe()) state.installLoadClassProbe()
    }

    private inner class State(
        private val param: XposedModuleInterface.PackageReadyParam
    ) {
        @Volatile
        private var executed = false

        @Volatile
        private var verifyingMarker = false

        fun installLifecycleHooks() = with(owner) {
            runCatching {
                ContextWrapper::class.java.method("attachBaseContext", Context::class.java).hook {
                    after {
                        (instance as? Application)?.let { application ->
                            collectFromApplication(application, "Application.attachBaseContext")
                        }
                        (args.firstOrNull() as? Context)?.let { context ->
                            tryClassLoader(context.classLoader, "Application.attachBaseContext context")
                            collectFromLoadedApk(context, "Application.attachBaseContext loadedApk")
                        }
                    }
                }
            }.onFailure { error ->
                HookerLogger.hookError("Failed to install attachBaseContext probe for ${appName()}", error)
            }

            runCatching {
                Application::class.java.method("onCreate").hook {
                    after { collectFromApplication(instance<Application>(), "Application.onCreate") }
                }
            }.onFailure { error ->
                HookerLogger.hookError("Failed to install Application.onCreate probe for ${appName()}", error)
            }

            runCatching {
                Instrumentation::class.java.method(
                    "callApplicationOnCreate",
                    Application::class.java
                ).hook {
                    after {
                        (args.firstOrNull() as? Application)?.let { application ->
                            collectFromApplication(application, "Instrumentation.callApplicationOnCreate")
                        }
                    }
                }
            }.onFailure { error ->
                HookerLogger.hookError(
                    "Failed to install Instrumentation.callApplicationOnCreate probe for ${appName()}",
                    error
                )
            }

            installLoadedApkHook()
        }

        private fun installLoadedApkHook() = with(owner) {
            runCatching {
                val loadedApkClass = HookerReflectionCache.loadedApkClass()
                HookerReflectionCache.declaredMethods(loadedApkClass)
                    .filter { method ->
                        method.name == "makeApplication" &&
                            Application::class.java.isAssignableFrom(method.returnType)
                    }
                    .forEach { method ->
                        method.hook {
                            after {
                                (result as? Application)?.let { application ->
                                    collectFromApplication(application, "LoadedApk.makeApplication")
                                }
                                tryClassLoader(
                                    owner.getField(instance, "mClassLoader") as? ClassLoader,
                                    "LoadedApk.makeApplication mClassLoader"
                                )
                            }
                        }
                    }
            }.onFailure { error ->
                HookerLogger.hookError("Failed to install LoadedApk.makeApplication probe for ${appName()}", error)
            }
        }

        fun scheduleRetries() {
            retryDelays().forEach { delay ->
                HookerRetryScheduler.postDelayed(delay) {
                    if (!executed) collectCurrentLoaders("retry ${delay}ms")
                }
            }
        }

        fun installDexClassLoaderHooks() = with(owner) {
            runCatching {
                BaseDexClassLoader::class.java.constructor()?.forEach { constructor ->
                    constructor.hook {
                        after { tryClassLoader(instance<ClassLoader>(), "BaseDexClassLoader.constructor") }
                    }
                }
            }.onFailure { error ->
                HookerLogger.hookError("Failed to install BaseDexClassLoader constructor probe for ${appName()}", error)
            }
        }

        fun installLoadClassProbe() = with(owner) {
            runCatching {
                ClassLoader::class.java.method("loadClass", String::class.java).hook {
                    after {
                        if (executed || verifyingMarker) return@after
                        val className = args.firstOrNull() as? String ?: return@after
                        if (className !in markerClasses()) return@after
                        tryClassLoader(instance<ClassLoader>(), "ClassLoader.loadClass($className)")
                    }
                }
            }.onFailure { error ->
                HookerLogger.hookError("Failed to install ClassLoader.loadClass probe for ${appName()}", error)
            }
        }

        fun tryClassLoader(loader: ClassLoader?, source: String): Boolean {
            if (executed) return true
            val candidate = loader ?: return false
            val marker = firstLoadableMarker(candidate) ?: return false
            return runWithClassLoader(candidate, source, marker)
        }

        private fun collectCurrentLoaders(source: String) {
            tryClassLoader(currentClassLoader(), "$source current")
            tryClassLoader(param.classLoader, "$source param")
            tryClassLoader(Thread.currentThread().contextClassLoader, "$source thread")
            runCatching {
                HookerReflectionCache.currentApplication()?.let { application ->
                    collectFromApplication(application, "$source currentApplication")
                }
            }.onFailure { error ->
                HookerLogger.hookError("Failed to query currentApplication for ${appName()}", error)
            }
        }

        private fun collectFromApplication(application: Application, source: String) {
            tryClassLoader(application.classLoader, "$source application")
            application.baseContext?.let { baseContext ->
                tryClassLoader(baseContext.classLoader, "$source baseContext")
                collectFromLoadedApk(baseContext, "$source baseContext loadedApk")
            }
            collectFromLoadedApk(application, "$source loadedApk")
        }

        @SuppressLint("DiscouragedPrivateApi", "PrivateApi")
        private fun collectFromLoadedApk(context: Context, source: String) {
            runCatching {
                val contextImplClass = HookerReflectionCache.contextImplClass()
                if (!contextImplClass.isInstance(context)) return@runCatching
                val packageInfo = HookerReflectionCache.declaredField(contextImplClass, "mPackageInfo")
                    .get(context)
                val loadedApkClassLoader = HookerReflectionCache.declaredField(
                    HookerReflectionCache.loadedApkClass(),
                    "mClassLoader"
                ).get(packageInfo) as? ClassLoader
                tryClassLoader(loadedApkClassLoader, source)
            }.onFailure { error ->
                HookerLogger.hookError(
                    "Failed to collect LoadedApk ClassLoader for ${appName()} from $source",
                    error
                )
            }
        }

        private fun firstLoadableMarker(loader: ClassLoader): String? {
            return markerClasses().firstOrNull { marker ->
                runCatching {
                    verifyingMarker = true
                    Class.forName(marker, false, loader)
                    true
                }.getOrDefault(false).also { verifyingMarker = false }
            }
        }

        @Synchronized
        private fun runWithClassLoader(loader: ClassLoader, source: String, marker: String): Boolean {
            if (executed) return true
            executed = true
            updateClassLoader(loader)
            HookerLogger.hookDebug(
                "JiaGu ClassLoader ready for ${appName()} from $source by $marker: ${loader.javaClass.name}"
            )
            owner.runHookFromCoordinator(param)
            return true
        }
    }
}
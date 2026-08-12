package com.hujiayucc.hook.hooker.util

import android.app.Application
import android.util.Log
import com.hujiayucc.hook.ModuleMain.Companion.module
import com.hujiayucc.hook.annotation.Run
import com.hujiayucc.hook.annotation.RunJiaGu
import com.hujiayucc.hook.hooker.sdk.GDT
import com.hujiayucc.hook.hooker.sdk.KW
import com.hujiayucc.hook.hooker.sdk.Pangle
import io.github.libxposed.api.XposedModuleInterface
import java.lang.reflect.Constructor
import java.lang.reflect.Executable
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

@Suppress("UNCHECKED_CAST")
abstract class Hooker {
    companion object {
        private val completedHookerKeys = ConcurrentHashMap.newKeySet<String>()
    }

    private val hookHandles = HookHandleRegistry()
    private val hookInstaller = HookInstaller(
        ownerClassName = { this@Hooker.javaClass.name },
        ownerName = { appName },
        fallbackClassLoader = { classLoader },
        handles = hookHandles
    )

    protected var appName: String = javaClass.simpleName
    protected var action: String = "Hook"
    protected var classLoader: ClassLoader? = null
    protected open val jiaGuMarkerClasses: List<String> = emptyList()
    protected open val jiaGuRetryDelays: List<Long> = listOf(100L, 300L, 800L, 1500L)
    protected open val jiaGuEnableLoadClassProbe: Boolean = true

    abstract fun XposedModuleInterface.PackageReadyParam.onPackageReady()
    fun call(param: XposedModuleInterface.PackageReadyParam) {
        classLoader = param.classLoader
        val isJiaGu = readHookMetadata()

        if (isJiaGu) {
            param.runJiaGuHook()
            return
        }

        param.runHookOnce()
    }

    internal fun callWithClassLoader(
        param: XposedModuleInterface.PackageReadyParam,
        loader: ClassLoader?
    ) {
        classLoader = loader ?: param.classLoader
        readHookMetadata()
        param.runHookOnce()
    }

    private fun readHookMetadata(): Boolean {
        var isJiaGu = false
        this.javaClass.annotations.forEach { annotation ->
            when (annotation) {
                is Run -> {
                    appName = annotation.appName
                    action = annotation.action
                }

                is RunJiaGu -> {
                    appName = annotation.appName
                    action = annotation.action
                    isJiaGu = true
                }
            }
        }
        return isJiaGu
    }

    private fun XposedModuleInterface.PackageReadyParam.runJiaGuHook() {
        JiaGuHookCoordinator(
            owner = this@Hooker,
            appName = { appName },
            currentClassLoader = { this@Hooker.classLoader },
            updateClassLoader = { this@Hooker.classLoader = it },
            markerClasses = { jiaGuMarkerClasses },
            retryDelays = { jiaGuRetryDelays },
            enableLoadClassProbe = { jiaGuEnableLoadClassProbe }
        ).run(this)
    }

    internal fun runHookFromCoordinator(param: XposedModuleInterface.PackageReadyParam) {
        with(param) { runHook() }
    }

    fun XposedModuleInterface.PackageReadyParam.runHookOnce() {
        val installKey = hookerInstallKey()
        if (!completedHookerKeys.add(installKey)) {
            logHookDebug("Skip completed hooker: $appName")
            return
        }
        runHook()
    }

    fun XposedModuleInterface.PackageReadyParam.runHook() {
        runCatching {
            onPackageReady()
        }.onSuccess {
            runCatching { module.log(Log.INFO, "Fuck AD", "$appName => $action") }
            logHookHandleSummary()
        }.onFailure { error ->
            completedHookerKeys.remove(hookerInstallKey())
            logHookError("Failed to run ${this@Hooker.javaClass.name} for $appName => $action", error)
        }
    }

    private fun hookerInstallKey(): String {
        val loader = classLoader
        val loaderId = loader?.let { System.identityHashCode(it).toString() } ?: "boot"
        return "${this@Hooker.javaClass.name}|$loaderId"
    }

    @Throws(ClassNotFoundException::class)
    fun String.toClass(): Class<*> {
        return classLoader?.loadClass(this) ?: throw ClassNotFoundException(this)
    }

    fun String.toClassOrNull(): Class<*>? {
        return try {
            classLoader?.loadClass(this)
        } catch (_: Throwable) {
            null
        }
    }

    fun Class<*>.method(name: String, vararg parameterTypes: Class<*>): Method {
        if (parameterTypes.isEmpty()) {
            return HookerReflectionCache.declaredMethods(this).first { it.name == name }
        }
        return HookerReflectionCache.exactMethod(this, name, parameterTypes.toList())
    }

    fun Class<*>.methodOrNull(name: String, vararg parameterTypes: Class<*>): Method? {
        return runCatching { method(name, *parameterTypes) }.getOrNull()
    }

    fun Class<*>.methodExact(name: String, vararg parameterTypes: Class<*>): Method {
        return HookerReflectionCache.exactMethod(this, name, parameterTypes.toList())
    }

    fun Class<*>.constructor(): Array<out Constructor<*>>? {
        return runCatching { HookerReflectionCache.declaredConstructors(this) }.getOrElse { error ->
            logHookError("Failed to get constructors: ${name}", error)
            null
        }
    }

    fun Class<*>.methods(name: String): List<Method> {
        return runCatching {
            HookerReflectionCache.declaredMethods(this).filter { it.name == name }
        }.getOrElse { error ->
            logHookError("Failed to get methods $name: ${this.name}", error)
            emptyList()
        }
    }

    protected fun Class<*>.cachedDeclaredMethods(): Array<Method> {
        return HookerReflectionCache.declaredMethods(this)
    }

    protected fun Class<*>.cachedMethods(): Array<Method> {
        return HookerReflectionCache.publicMethods(this)
    }

    private fun Executable.hookExecutable(block: HookDsl.() -> Unit) {
        val dsl = hookInstaller.buildDsl(this, block) ?: return
        runCatching { hookInstaller.install(this, dsl) }.onFailure { error ->
            logHookError("Failed to install hook: ${toGenericString()}", error)
        }
    }

    private fun logHookHandleSummary(owner: String = appName) {
        val ownerHandleCount = hookHandleCount(owner)
        if (ownerHandleCount > 0) {
            logHookDebug("Registered $ownerHandleCount hook handles for $owner")
        }
    }

    protected fun hookHandleCount(owner: String = appName): Int {
        return hookHandles.count(owner)
    }

    fun Method.hook(block: HookDsl.() -> Unit) {
        hookExecutable(block)
    }

    fun Constructor<*>.hook(block: HookDsl.() -> Unit) {
        hookExecutable(block)
    }

    fun List<Method>?.hook(block: HookDsl.() -> Unit) {
        if (this.isNullOrEmpty()) return
        asSequence()
            .distinctBy { it.toGenericString() }
            .forEach { method -> method.hook(block) }
    }

    fun Array<Method>?.hook(block: HookDsl.() -> Unit) {
        if (this.isNullOrEmpty()) return
        asSequence()
            .distinctBy { it.toGenericString() }
            .forEach { method -> method.hook(block) }
    }

    fun getField(obj: Any, fieldName: String): Any? {
        return runCatching {
            HookerReflectionCache.declaredField(obj.javaClass, fieldName).get(obj)
        }.getOrElse { error ->
            logHookError("Failed to get field $fieldName: ${obj.javaClass.name}", error)
            null
        }
    }

    fun setField(obj: Any, fieldName: String, value: Any?) {
        runCatching {
            HookerReflectionCache.declaredField(obj.javaClass, fieldName).set(obj, value)
        }.onFailure { error ->
            logHookError("Failed to set field $fieldName: ${obj.javaClass.name}", error)
        }
    }

    fun isMainProcess(param: XposedModuleInterface.PackageReadyParam): Boolean {
        val packageName = param.packageName
        val processName = currentProcessName() ?: return true
        return processName == packageName
    }

    private fun currentProcessName(): String? {
        return runCatching {
            Application.getProcessName()
        }.getOrNull() ?: runCatching {
            HookerReflectionCache.currentProcessName()
        }.getOrNull()
    }

    fun loadSdk(
        param: XposedModuleInterface.PackageReadyParam,
        pangle: Boolean = false,
        gdt: Boolean = false,
        kw: Boolean = false
    ) {
        val currentClassLoader = classLoader
        if (pangle) Pangle.callWithClassLoader(param, currentClassLoader)
        if (gdt) GDT.callWithClassLoader(param, currentClassLoader)
        if (kw) KW.callWithClassLoader(param, currentClassLoader)
    }

    protected fun logI(message: String, throwable: Throwable? = null) {
        HookerLogger.info(message, throwable)
    }

    protected fun logD(message: String, throwable: Throwable? = null) {
        HookerLogger.debug(message, throwable)
    }

    protected fun logE(message: String, throwable: Throwable? = null) {
        HookerLogger.error(message, throwable)
    }

    protected fun logW(message: String, throwable: Throwable? = null) {
        HookerLogger.warn(message, throwable)
    }

    protected fun logHookDebug(message: String) {
        HookerLogger.hookDebug(message)
    }

    private fun logHookError(message: String, throwable: Throwable? = null) {
        HookerLogger.hookError(message, throwable)
    }

    protected fun runMain(function: () -> Unit) {
        HookerRetryScheduler.post(function)
    }

    protected fun runMainDelayed(delayMillis: Long, function: () -> Unit) {
        HookerRetryScheduler.postDelayed(delayMillis, function)
    }

    val HookCallback.instance: Any get() = thisObject as Any
    fun <T> HookCallback.instance(): T = thisObject as T
}
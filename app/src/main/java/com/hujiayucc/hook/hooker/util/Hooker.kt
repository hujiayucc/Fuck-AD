package com.hujiayucc.hook.hooker.util

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.hujiayucc.hook.ModuleMain.Companion.module
import com.hujiayucc.hook.ModuleMain.Companion.prefs
import com.hujiayucc.hook.annotation.Run
import com.hujiayucc.hook.annotation.RunJiaGu
import com.hujiayucc.hook.hooker.sdk.GDT
import com.hujiayucc.hook.hooker.sdk.KW
import com.hujiayucc.hook.hooker.sdk.Pangle
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModuleInterface
import java.lang.reflect.Constructor
import java.lang.reflect.Executable
import java.lang.reflect.Method

@Suppress("UNCHECKED_CAST")
abstract class Hooker {
    private object UnsetResult

    protected var appName: String = javaClass.simpleName
    protected var action: String = "Hook"
    protected var classLoader: ClassLoader? = null

    abstract fun XposedModuleInterface.PackageReadyParam.onPackageReady()
    fun call(param: XposedModuleInterface.PackageReadyParam) {
        classLoader = param.classLoader
        val isJiaGu = readHookMetadata()

        if (isJiaGu) {
            runCatching {
                classLoader = getRealClassLoader()
            }.onFailure { error ->
                logHookError("Failed to get real ClassLoader for $appName", error)
            }
        }

        param.runHook()
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

    @SuppressLint("PrivateApi", "DiscouragedPrivateApi")
    private fun getRealClassLoader(): ClassLoader {
        val threadCl = Thread.currentThread().contextClassLoader
        if (threadCl != null && threadCl !== classLoader) return threadCl

        var realClassLoader: ClassLoader? = null
        module.hook(Application::class.java.method("attachBaseContext")).intercept { chain ->
            val result = chain.proceedWith(chain.thisObject, chain.args.toTypedArray())
            val context = chain.args[0] as Context
            realClassLoader = context.classLoader
            result
        }

        if (realClassLoader != null && realClassLoader !== classLoader) return realClassLoader

        try {
            val activityThread = Class.forName("android.app.ActivityThread")
            val currentApp = activityThread.getDeclaredMethod("currentApplication").invoke(null) as? Application
            currentApp?.classLoader?.let { appCl ->
                if (appCl !== classLoader) return appCl
            }
        } catch (_: Throwable) {}

        return classLoader!!
    }

    fun XposedModuleInterface.PackageReadyParam.runHook() {
        runCatching {
            onPackageReady()
        }.onSuccess {
            runCatching { module.log(Log.INFO, "Fuck AD", "$appName => $action") }
        }.onFailure { error ->
            logHookError("Failed to run ${this@Hooker.javaClass.name} for $appName => $action", error)
        }
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
            return declaredMethods.first { it.name == name }
        }
        return getDeclaredMethod(name, *parameterTypes)
    }

    fun Class<*>.methodOrNull(name: String, vararg parameterTypes: Class<*>): Method? {
        return runCatching { method(name, *parameterTypes) }.getOrNull()
    }

    fun Class<*>.constructor(): Array<out Constructor<*>>? {
        return runCatching { declaredConstructors }.getOrElse { error ->
            logHookError("Failed to get constructors: ${name}", error)
            null
        }
    }

    fun Class<*>.methods(name: String): List<Method> {
        return runCatching { declaredMethods.filter { it.name == name } }.getOrElse { error ->
            logHookError("Failed to get methods $name: ${this.name}", error)
            emptyList()
        }
    }

    fun Class<*>.methodContains(name: String): List<Method> {
        return runCatching { declaredMethods.filter { it.name.contains(name) } }.getOrElse { error ->
            logHookError("Failed to get methods contains $name: ${this.name}", error)
            emptyList()
        }
    }

    class HookDsl internal constructor() {
        internal var replaceBlock: (HookCallback.() -> Any?)? = null
        internal var replaceUnitBlock: (HookCallback.() -> Unit)? = null
        internal var beforeBlock: (HookCallback.() -> Unit)? = null
        internal var afterBlock: (HookCallback.() -> Unit)? = null

        fun replace(block: HookCallback.() -> Any?) {
            replaceBlock = block
        }

        fun replaceTo(value: Any?) {
            replaceBlock = { value }
        }

        fun replaceUnit(block: HookCallback.() -> Unit = {}) {
            replaceUnitBlock = block
        }

        fun before(block: HookCallback.() -> Unit) {
            beforeBlock = block
        }

        fun after(block: HookCallback.() -> Unit) {
            afterBlock = block
        }
    }

    private fun Executable.hookInternal(dsl: HookDsl) {
        module.hook(this).intercept { hookChain ->
            var currentResult: Any? = UnsetResult
            var originalExecuted = false
            val callback = object : HookCallback {
                override val chain: XposedInterface.Chain
                    get() = hookChain
                override var result: Any?
                    get() = if (currentResult === UnsetResult) null else currentResult
                    set(value) {
                        currentResult = value
                    }
            }

            fun proceedOriginal(): Any? {
                originalExecuted = true
                return hookChain.proceedWith(hookChain.thisObject, hookChain.args.toTypedArray())
            }

            dsl.replaceBlock?.let { replace ->
                return@intercept try {
                    replace.invoke(callback)
                } catch (error: Throwable) {
                    logHookError("Hook replace failed: ${toGenericString()}", error)
                    proceedOriginal()
                }
            }
            dsl.replaceUnitBlock?.let { replaceUnit ->
                return@intercept try {
                    replaceUnit.invoke(callback)
                    null
                } catch (error: Throwable) {
                    logHookError("Hook replaceUnit failed: ${toGenericString()}", error)
                    proceedOriginal()
                }
            }

            dsl.beforeBlock?.let { before ->
                runCatching { before.invoke(callback) }.onFailure { error ->
                    logHookError("Hook before failed: ${toGenericString()}", error)
                }
            }
            if (currentResult === UnsetResult) {
                callback.result = proceedOriginal()
            }

            if (originalExecuted) {
                dsl.afterBlock?.let { after ->
                    runCatching { after.invoke(callback) }.onFailure { error ->
                        logHookError("Hook after failed: ${toGenericString()}", error)
                    }
                }
            }
            callback.result
        }
    }

    private fun HookDsl.isValid(): Boolean {
        val replaceCount = listOf(replaceBlock, replaceUnitBlock).count { it != null }
        val callbackCount = listOf(beforeBlock, afterBlock).count { it != null }
        return (replaceCount == 1 && callbackCount == 0) || (replaceCount == 0 && callbackCount > 0)
    }

    private fun Executable.hookExecutable(block: HookDsl.() -> Unit) {
        val dsl = runCatching { HookDsl().apply(block) }.getOrElse { error ->
            logHookError("Failed to build Hook DSL: ${toGenericString()}", error)
            return
        }
        if (!dsl.isValid()) {
            logHookError(
                "Invalid Hook DSL: ${toGenericString()}",
                IllegalArgumentException("Hook DSL requires replace/replaceTo/replaceUnit, or before/after.")
            )
            return
        }
        runCatching { hookInternal(dsl) }.onFailure { error ->
            logHookError("Failed to install hook: ${toGenericString()}", error)
        }
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
            obj.javaClass.getDeclaredField(fieldName).apply { isAccessible = true }.get(obj)
        }.getOrElse { error ->
            logHookError("Failed to get field $fieldName: ${obj.javaClass.name}", error)
            null
        }
    }

    fun setField(obj: Any, fieldName: String, value: Any?) {
        runCatching {
            obj.javaClass.getDeclaredField(fieldName).apply { isAccessible = true }.set(obj, value)
        }.onFailure { error ->
            logHookError("Failed to set field $fieldName: ${obj.javaClass.name}", error)
        }
    }

    fun loadSdk(
        param: XposedModuleInterface.PackageReadyParam,
        pangle: Boolean = false,
        gdt: Boolean = false,
        kw: Boolean = false
    ) {
        if (pangle) Pangle.call(param)
        if (gdt) GDT.call(param)
        if (kw) KW.call(param)
    }

    fun loadAllSDK(param: XposedModuleInterface.PackageReadyParam) {
        GDT.call(param)
        KW.call(param)
        Pangle.call(param)
    }

    protected fun logI(message: String, throwable: Throwable? = null) {
        module.log(Log.INFO, "Fuck AD", message, throwable)
    }

    protected fun logD(message: String, throwable: Throwable? = null) {
        module.log(Log.DEBUG, "Fuck AD", message, throwable)
    }

    protected fun logE(message: String, throwable: Throwable? = null) {
        module.log(Log.ERROR, "Fuck AD", message, throwable)
    }

    protected fun logW(message: String, throwable: Throwable? = null) {
        module.log(Log.WARN, "Fuck AD", message, throwable)
    }

    private fun logHookError(message: String, throwable: Throwable? = null) {
        val shouldLog = runCatching { prefs.getBoolean("errorLog", false) }.getOrDefault(false)
        if (shouldLog) runCatching { logE(message, throwable) }
    }

    protected inline fun runMain(crossinline function: () -> Unit) {
        Handler(Looper.getMainLooper()).post { function() }
    }

    protected inline fun runMainDelayed(delayMillis: Long, crossinline function: () -> Unit) {
        Handler(Looper.getMainLooper()).postDelayed({ function() }, delayMillis)
    }

    val HookCallback.instance: Any get() = chain.thisObject
    val HookCallback.args: List<Any?> get() = chain.args
    fun <T> HookCallback.instance(): T = chain.thisObject as T
}
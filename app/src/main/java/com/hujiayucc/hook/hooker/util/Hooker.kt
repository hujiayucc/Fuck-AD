package com.hujiayucc.hook.hooker.util

import android.annotation.SuppressLint
import android.app.Application
import android.app.Instrumentation
import android.content.Context
import android.content.ContextWrapper
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
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import dalvik.system.BaseDexClassLoader

@Suppress("UNCHECKED_CAST")
abstract class Hooker {
    companion object {
        private val installedHookKeys = ConcurrentHashMap.newKeySet<String>()
        private val completedHookerKeys = ConcurrentHashMap.newKeySet<String>()
    }

    private object UnsetResult

    private val hookHandles = Collections.synchronizedList(mutableListOf<RegisteredHook>())

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
        if (jiaGuMarkerClasses.isEmpty()) {
            logHookError(
                "Skip $appName JiaGu hook because marker classes are empty",
                IllegalStateException("@RunJiaGu requires marker classes for real ClassLoader verification.")
            )
            return
        }

        val state = JiaGuHookState(this)
        if (state.tryClassLoader(paramClassLoader = classLoader, source = "PackageReadyParam")) return
        state.installLifecycleHooks()
        state.installDexClassLoaderHooks()
        state.scheduleRetries()
        if (jiaGuEnableLoadClassProbe) state.installLoadClassProbe()
    }

    private inner class JiaGuHookState(
        private val param: XposedModuleInterface.PackageReadyParam
    ) {
        @Volatile
        private var executed = false
        @Volatile
        private var verifyingMarker = false

        fun installLifecycleHooks() {
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
                logHookError("Failed to install attachBaseContext probe for $appName", error)
            }

            runCatching {
                Application::class.java.method("onCreate").hook {
                    after {
                        collectFromApplication(instance<Application>(), "Application.onCreate")
                    }
                }
            }.onFailure { error ->
                logHookError("Failed to install Application.onCreate probe for $appName", error)
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
                logHookError("Failed to install Instrumentation.callApplicationOnCreate probe for $appName", error)
            }

            installLoadedApkHook()
        }

        private fun installLoadedApkHook() {
            runCatching {
                val loadedApkClass = Class.forName("android.app.LoadedApk")
                loadedApkClass.declaredMethods
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
                                    getField(instance, "mClassLoader") as? ClassLoader,
                                    "LoadedApk.makeApplication mClassLoader"
                                )
                            }
                        }
                    }
            }.onFailure { error ->
                logHookError("Failed to install LoadedApk.makeApplication probe for $appName", error)
            }
        }

        fun scheduleRetries() {
            jiaGuRetryDelays.forEach { delay ->
                runMainDelayed(delay) {
                    if (!executed) collectCurrentLoaders("retry ${delay}ms")
                }
            }
        }

        fun installDexClassLoaderHooks() {
            runCatching {
                BaseDexClassLoader::class.java.constructor()?.forEach { constructor ->
                    constructor.hook {
                        after {
                            tryClassLoader(instance<ClassLoader>(), "BaseDexClassLoader.constructor")
                        }
                    }
                }
            }.onFailure { error ->
                logHookError("Failed to install BaseDexClassLoader constructor probe for $appName", error)
            }
        }

        fun installLoadClassProbe() {
            runCatching {
                ClassLoader::class.java.method("loadClass", String::class.java).hook {
                    after {
                        if (executed || verifyingMarker) return@after
                        val className = args.firstOrNull() as? String ?: return@after
                        if (className !in jiaGuMarkerClasses) return@after
                        tryClassLoader(instance<ClassLoader>(), "ClassLoader.loadClass($className)")
                    }
                }
            }.onFailure { error ->
                logHookError("Failed to install ClassLoader.loadClass probe for $appName", error)
            }
        }

        fun tryClassLoader(paramClassLoader: ClassLoader?, source: String): Boolean {
            if (executed) return true
            val loader = paramClassLoader ?: return false
            val marker = firstLoadableMarker(loader) ?: return false
            return runWithClassLoader(loader, source, marker)
        }

        private fun collectCurrentLoaders(source: String) {
            tryClassLoader(classLoader, "$source current")
            tryClassLoader(param.classLoader, "$source param")
            tryClassLoader(Thread.currentThread().contextClassLoader, "$source thread")
            runCatching {
                val activityThread = Class.forName("android.app.ActivityThread")
                val currentApp = activityThread.getDeclaredMethod("currentApplication").invoke(null) as? Application
                currentApp?.let { collectFromApplication(it, "$source currentApplication") }
            }.onFailure { error ->
                logHookError("Failed to query currentApplication for $appName", error)
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
                val contextImplClass = Class.forName("android.app.ContextImpl")
                if (!contextImplClass.isInstance(context)) return@runCatching
                val packageInfo = contextImplClass.getDeclaredField("mPackageInfo")
                    .apply { isAccessible = true }
                    .get(context)
                val loadedApkClass = Class.forName("android.app.LoadedApk")
                val loadedApkClassLoader = loadedApkClass.getDeclaredField("mClassLoader")
                    .apply { isAccessible = true }
                    .get(packageInfo) as? ClassLoader
                tryClassLoader(loadedApkClassLoader, source)
            }.onFailure { error ->
                logHookError("Failed to collect LoadedApk ClassLoader for $appName from $source", error)
            }
        }

        private fun firstLoadableMarker(loader: ClassLoader): String? {
            return jiaGuMarkerClasses.firstOrNull { marker ->
                runCatching {
                    verifyingMarker = true
                    Class.forName(marker, false, loader)
                    true
                }.getOrDefault(false).also {
                    verifyingMarker = false
                }
            }
        }

        @Synchronized
        private fun runWithClassLoader(loader: ClassLoader, source: String, marker: String): Boolean {
            if (executed) return true
            executed = true
            classLoader = loader
            logHookDebug("JiaGu ClassLoader ready for $appName from $source by $marker: ${loader.javaClass.name}")
            param.runHook()
            return true
        }
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
            return declaredMethods.first { it.name == name }
        }
        return getDeclaredMethod(name, *parameterTypes)
    }

    fun Class<*>.methodOrNull(name: String, vararg parameterTypes: Class<*>): Method? {
        return runCatching { method(name, *parameterTypes) }.getOrNull()
    }

    fun Class<*>.methodExact(name: String, vararg parameterTypes: Class<*>): Method {
        return getDeclaredMethod(name, *parameterTypes)
    }

    fun Class<*>.methodExactOrNull(name: String, vararg parameterTypes: Class<*>): Method? {
        return runCatching { methodExact(name, *parameterTypes) }.getOrNull()
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

    class HookDsl internal constructor(
        private val onDuplicate: (String) -> Unit = {}
    ) {
        internal var replaceBlock: (HookCallback.() -> Any?)? = null
        internal var replaceUnitBlock: (HookCallback.() -> Unit)? = null
        internal var beforeBlock: (HookCallback.() -> Unit)? = null
        internal var afterBlock: (HookCallback.() -> Unit)? = null

        fun replace(block: HookCallback.() -> Any?) {
            if (replaceBlock != null) onDuplicate("replace/replaceTo")
            replaceBlock = block
        }

        fun replaceTo(value: Any?) {
            if (replaceBlock != null) onDuplicate("replace/replaceTo")
            replaceBlock = { value }
        }

        fun replaceUnit(block: HookCallback.() -> Unit = {}) {
            if (replaceUnitBlock != null) onDuplicate("replaceUnit")
            replaceUnitBlock = block
        }

        fun before(block: HookCallback.() -> Unit) {
            if (beforeBlock != null) onDuplicate("before")
            beforeBlock = block
        }

        fun after(block: HookCallback.() -> Unit) {
            if (afterBlock != null) onDuplicate("after")
            afterBlock = block
        }
    }

    private fun Executable.hookInternal(dsl: HookDsl) {
        val installKey = hookInstallKey()
        if (!installedHookKeys.add(installKey)) {
            logHookDebug("Skip duplicate hook: ${toGenericString()}")
            return
        }

        try {
            val handle = module.hook(this).intercept { hookChain ->
                var currentResult: Any? = UnsetResult
                var originalExecuted = false

                fun currentResultOrNull(): Any? {
                    return if (currentResult === UnsetResult) null else currentResult
                }

                fun proceedOriginalWithNonNull(thisObject: Any, args: Array<Any?>): Any? {
                    if (originalExecuted) {
                        logHookError(
                            "Original method already proceeded: ${toGenericString()}",
                            IllegalStateException("Use HookCallback.result instead of proceeding twice.")
                        )
                        return currentResultOrNull()
                    }
                    originalExecuted = true
                    val proceededResult = hookChain.proceedWith(thisObject, args)
                    currentResult = proceededResult
                    return proceededResult
                }

                fun proceedOriginalWith(thisObject: Any?, args: Array<Any?>): Any? {
                    if (thisObject == null) {
                        logHookError(
                            "Cannot proceed with null thisObject: ${toGenericString()}",
                            IllegalArgumentException("thisObject must be non-null for proceedWith.")
                        )
                        return currentResultOrNull()
                    }
                    return proceedOriginalWithNonNull(thisObject, args)
                }

                fun proceedOriginal(args: Array<Any?>? = null): Any? {
                    if (originalExecuted) {
                        logHookError(
                            "Original method already proceeded: ${toGenericString()}",
                            IllegalStateException("Use HookCallback.result instead of proceeding twice.")
                        )
                        return currentResultOrNull()
                    }
                    if (args != null) return proceedOriginalWith(hookChain.thisObject, args)

                    originalExecuted = true
                    val proceededResult = hookChain.proceed()
                    currentResult = proceededResult
                    return proceededResult
                }

                val callback = object : HookCallback {
                    override val chain: XposedInterface.Chain
                        get() = hookChain
                    override val thisObject: Any?
                        get() = hookChain.thisObject
                    override val args: List<Any?>
                        get() = hookChain.args
                    override val hasResult: Boolean
                        get() = currentResult !== UnsetResult
                    override var result: Any?
                        get() = currentResultOrNull()
                        set(value) {
                            currentResult = value
                        }

                    override fun proceed(): Any? {
                        return proceedOriginal()
                    }

                    override fun proceedWith(args: Array<Any?>): Any? {
                        return proceedOriginal(args)
                    }

                    override fun proceedWith(thisObject: Any?, args: Array<Any?>): Any? {
                        return proceedOriginalWith(thisObject, args)
                    }
                }

                dsl.replaceBlock?.let { replace ->
                    return@intercept try {
                        replace.invoke(callback).also { currentResult = it }
                    } catch (error: Throwable) {
                        logHookError("Hook replace failed: ${toGenericString()}", error)
                        callback.proceed()
                    }
                }
                dsl.replaceUnitBlock?.let { replaceUnit ->
                    return@intercept try {
                        replaceUnit.invoke(callback)
                        null
                    } catch (error: Throwable) {
                        logHookError("Hook replaceUnit failed: ${toGenericString()}", error)
                        callback.proceed()
                    }
                }

                dsl.beforeBlock?.let { before ->
                    runCatching { before.invoke(callback) }.onFailure { error ->
                        logHookError("Hook before failed: ${toGenericString()}", error)
                    }
                }
                if (!callback.hasResult) {
                    callback.proceed()
                }

                dsl.afterBlock?.let { after ->
                    runCatching { after.invoke(callback) }.onFailure { error ->
                        logHookError("Hook after failed: ${toGenericString()}", error)
                    }
                }
                callback.result
            }
            registerHookHandle(this, handle)
        } catch (error: Throwable) {
            installedHookKeys.remove(installKey)
            throw error
        }
    }

    private fun Executable.hookInstallKey(): String {
        val loader = declaringClass.classLoader ?: classLoader
        val loaderId = loader?.let { System.identityHashCode(it).toString() } ?: "boot"
        return "${this@Hooker.javaClass.name}|$loaderId|${toGenericString()}"
    }

    private fun registerHookHandle(executable: Executable, handle: Any) {
        hookHandles += RegisteredHook(
            owner = appName,
            executable = executable.toGenericString(),
            handle = handle
        )
    }

    private fun logHookHandleSummary(owner: String = appName) {
        val ownerHandleCount = hookHandleCount(owner)
        if (ownerHandleCount > 0) {
            logHookDebug("Registered $ownerHandleCount hook handles for $owner")
        }
    }

    protected fun hookHandleCount(owner: String = appName): Int {
        return synchronized(hookHandles) {
            hookHandles.count { registeredHook -> registeredHook.owner == owner }
        }
    }

    protected fun hookHandleExecutables(owner: String = appName): List<String> {
        return synchronized(hookHandles) {
            hookHandles
                .filter { registeredHook -> registeredHook.owner == owner }
                .map { registeredHook -> registeredHook.executable }
        }
    }

    protected fun logHookHandles(owner: String = appName) {
        val executables = hookHandleExecutables(owner)
        logHookDebug("Registered ${executables.size} hook handles for $owner")
        executables.forEach { executable ->
            logHookDebug("Hook handle for $owner: $executable")
        }
    }

    protected fun clearHookHandleRecords(owner: String = appName): Int {
        var removedCount = 0
        synchronized(hookHandles) {
            val iterator = hookHandles.iterator()
            while (iterator.hasNext()) {
                if (iterator.next().owner == owner) {
                    iterator.remove()
                    removedCount++
                }
            }
        }
        logHookDebug("Cleared $removedCount hook handle records for $owner")
        return removedCount
    }

    private data class RegisteredHook(
        val owner: String,
        val executable: String,
        val handle: Any
    )

    private fun HookDsl.isValid(): Boolean {
        val replaceCount = listOf(replaceBlock, replaceUnitBlock).count { it != null }
        val callbackCount = listOf(beforeBlock, afterBlock).count { it != null }
        return (replaceCount == 1 && callbackCount == 0) || (replaceCount == 0 && callbackCount > 0)
    }

    private fun HookDsl.describeCallbacks(): String {
        val callbacks = mutableListOf<String>()
        if (replaceBlock != null) callbacks += "replace"
        if (replaceUnitBlock != null) callbacks += "replaceUnit"
        if (beforeBlock != null) callbacks += "before"
        if (afterBlock != null) callbacks += "after"
        return if (callbacks.isEmpty()) "none" else callbacks.joinToString()
    }

    private fun Executable.canReplaceUnit(): Boolean {
        return this is Method && (returnType == Void.TYPE || returnType == Void::class.java)
    }

    private fun Executable.validateDsl(dsl: HookDsl): Boolean {
        if (!dsl.isValid()) {
            logHookError(
                "Invalid Hook DSL: ${toGenericString()} callbacks=${dsl.describeCallbacks()}",
                IllegalArgumentException("Hook DSL requires replace/replaceTo/replaceUnit, or before/after.")
            )
            return false
        }
        if (dsl.replaceUnitBlock != null && !canReplaceUnit()) {
            logHookError(
                "Suspicious replaceUnit target: ${toGenericString()} callbacks=${dsl.describeCallbacks()}",
                IllegalArgumentException("replaceUnit is safest with a void return type; use replace/replaceTo for explicit return values.")
            )
        }
        return true
    }

    private fun Executable.hookExecutable(block: HookDsl.() -> Unit) {
        val dsl = runCatching {
            HookDsl { name ->
                logHookError(
                    "Duplicate Hook DSL callback $name: ${toGenericString()}",
                    IllegalStateException("Only the last $name block is used.")
                )
            }.apply(block)
        }.getOrElse { error ->
            logHookError("Failed to build Hook DSL: ${toGenericString()}", error)
            return
        }
        if (!validateDsl(dsl)) return
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

    fun isMainProcess(param: XposedModuleInterface.PackageReadyParam): Boolean {
        val packageName = param.packageName
        val processName = currentProcessName() ?: return true
        return processName == packageName
    }

    private fun currentProcessName(): String? {
        return runCatching {
            Application.getProcessName()
        }.getOrNull() ?: runCatching {
            Class.forName("android.app.ActivityThread")
                .getDeclaredMethod("currentProcessName")
                .invoke(null) as? String
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

    fun loadAllSDK(param: XposedModuleInterface.PackageReadyParam) {
        val currentClassLoader = classLoader
        GDT.callWithClassLoader(param, currentClassLoader)
        KW.callWithClassLoader(param, currentClassLoader)
        Pangle.callWithClassLoader(param, currentClassLoader)
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

    protected fun logHookDebug(message: String) {
        val shouldLog = runCatching { prefs.getBoolean("errorLog", false) }.getOrDefault(false)
        if (shouldLog) runCatching { logD(message) }
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

    val HookCallback.instance: Any get() = thisObject as Any
    fun <T> HookCallback.instance(): T = thisObject as T
}
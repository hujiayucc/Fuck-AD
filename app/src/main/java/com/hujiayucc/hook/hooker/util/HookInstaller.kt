package com.hujiayucc.hook.hooker.util

import com.hujiayucc.hook.ModuleMain.Companion.module
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Executable
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

internal class HookInstaller(
    private val ownerClassName: () -> String,
    private val ownerName: () -> String,
    private val fallbackClassLoader: () -> ClassLoader?,
    private val handles: HookHandleRegistry
) {
    private object UnsetResult

    fun install(executable: Executable, dsl: HookDsl) {
        val installKey = executable.installKey()
        if (!installedHookKeys.add(installKey)) {
            HookerLogger.hookDebug("Skip duplicate hook: ${executable.toGenericString()}")
            return
        }

        try {
            val handle = module.hook(executable).intercept { hookChain ->
                var currentResult: Any? = UnsetResult
                var originalExecuted = false

                fun currentResultOrNull(): Any? {
                    return if (currentResult === UnsetResult) null else currentResult
                }

                fun proceedOriginalWithNonNull(thisObject: Any, args: Array<Any?>): Any? {
                    if (originalExecuted) {
                        HookerLogger.hookError(
                            "Original method already proceeded: ${executable.toGenericString()}",
                            IllegalStateException("Use HookCallback.result instead of proceeding twice.")
                        )
                        return currentResultOrNull()
                    }
                    originalExecuted = true
                    return hookChain.proceedWith(thisObject, args).also { currentResult = it }
                }

                fun proceedOriginalWith(thisObject: Any?, args: Array<Any?>): Any? {
                    if (thisObject == null) {
                        HookerLogger.hookError(
                            "Cannot proceed with null thisObject: ${executable.toGenericString()}",
                            IllegalArgumentException("thisObject must be non-null for proceedWith.")
                        )
                        return currentResultOrNull()
                    }
                    return proceedOriginalWithNonNull(thisObject, args)
                }

                fun proceedOriginal(args: Array<Any?>? = null): Any? {
                    if (originalExecuted) {
                        HookerLogger.hookError(
                            "Original method already proceeded: ${executable.toGenericString()}",
                            IllegalStateException("Use HookCallback.result instead of proceeding twice.")
                        )
                        return currentResultOrNull()
                    }
                    if (args != null) return proceedOriginalWith(hookChain.thisObject, args)

                    originalExecuted = true
                    return hookChain.proceed().also { currentResult = it }
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

                    override fun proceed(): Any? = proceedOriginal()

                    override fun proceedWith(args: Array<Any?>): Any? = proceedOriginal(args)

                    override fun proceedWith(thisObject: Any?, args: Array<Any?>): Any? {
                        return proceedOriginalWith(thisObject, args)
                    }
                }

                dsl.replaceBlock?.let { replace ->
                    return@intercept try {
                        replace.invoke(callback).also { currentResult = it }
                    } catch (error: Throwable) {
                        HookerLogger.hookError("Hook replace failed: ${executable.toGenericString()}", error)
                        callback.proceed()
                    }
                }
                dsl.replaceUnitBlock?.let { replaceUnit ->
                    return@intercept try {
                        replaceUnit.invoke(callback)
                        null
                    } catch (error: Throwable) {
                        HookerLogger.hookError("Hook replaceUnit failed: ${executable.toGenericString()}", error)
                        callback.proceed()
                    }
                }

                dsl.beforeBlock?.let { before ->
                    runCatching { before.invoke(callback) }.onFailure { error ->
                        HookerLogger.hookError("Hook before failed: ${executable.toGenericString()}", error)
                    }
                }
                if (!callback.hasResult) callback.proceed()

                dsl.afterBlock?.let { after ->
                    runCatching { after.invoke(callback) }.onFailure { error ->
                        HookerLogger.hookError("Hook after failed: ${executable.toGenericString()}", error)
                    }
                }
                callback.result
            }
            handles.register(ownerName(), executable, handle)
        } catch (error: Throwable) {
            installedHookKeys.remove(installKey)
            throw error
        }
    }

    fun buildDsl(executable: Executable, block: HookDsl.() -> Unit): HookDsl? {
        val dsl = runCatching {
            HookDsl { name ->
                HookerLogger.hookError(
                    "Duplicate Hook DSL callback $name: ${executable.toGenericString()}",
                    IllegalStateException("Only the last $name block is used.")
                )
            }.apply(block)
        }.getOrElse { error ->
            HookerLogger.hookError("Failed to build Hook DSL: ${executable.toGenericString()}", error)
            return null
        }
        return dsl.takeIf { executable.validate(it) }
    }

    private fun Executable.installKey(): String {
        val loader = declaringClass.classLoader ?: fallbackClassLoader()
        val loaderId = loader?.let { System.identityHashCode(it).toString() } ?: "boot"
        return "${ownerClassName()}|$loaderId|${toGenericString()}"
    }

    private fun Executable.validate(dsl: HookDsl): Boolean {
        if (!dsl.isValid()) {
            HookerLogger.hookError(
                "Invalid Hook DSL: ${toGenericString()} callbacks=${dsl.describeCallbacks()}",
                IllegalArgumentException("Hook DSL requires replace/replaceTo/replaceUnit, or before/after.")
            )
            return false
        }
        if (dsl.replaceUnitBlock != null && this is Method &&
            returnType != Void.TYPE && returnType != Void::class.java
        ) {
            HookerLogger.hookError(
                "Suspicious replaceUnit target: ${toGenericString()} callbacks=${dsl.describeCallbacks()}",
                IllegalArgumentException("replaceUnit is safest with a void return type; use replace/replaceTo for explicit return values.")
            )
        }
        return true
    }

    private companion object {
        val installedHookKeys = ConcurrentHashMap.newKeySet<String>()
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

    internal fun isValid(): Boolean {
        val replaceCount = listOf(replaceBlock, replaceUnitBlock).count { it != null }
        val callbackCount = listOf(beforeBlock, afterBlock).count { it != null }
        return (replaceCount == 1 && callbackCount == 0) || (replaceCount == 0 && callbackCount > 0)
    }

    internal fun describeCallbacks(): String {
        val callbacks = mutableListOf<String>()
        if (replaceBlock != null) callbacks += "replace"
        if (replaceUnitBlock != null) callbacks += "replaceUnit"
        if (beforeBlock != null) callbacks += "before"
        if (afterBlock != null) callbacks += "after"
        return if (callbacks.isEmpty()) "none" else callbacks.joinToString()
    }
}

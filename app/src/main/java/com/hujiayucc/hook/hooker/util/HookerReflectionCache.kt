package com.hujiayucc.hook.hooker.util

import android.app.Application
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

internal object HookerReflectionCache {
    private val declaredMethodCache = ConcurrentHashMap<Class<*>, Array<Method>>()
    private val publicMethodCache = ConcurrentHashMap<Class<*>, Array<Method>>()
    private val exactMethodCache = ConcurrentHashMap<MethodLookupKey, Method>()
    private val declaredConstructorCache = ConcurrentHashMap<Class<*>, Array<out Constructor<*>>>()
    private val declaredFieldCache = ConcurrentHashMap<FieldLookupKey, Field>()

    @Volatile
    private var activityThreadClass: Class<*>? = null

    @Volatile
    private var currentApplicationMethod: Method? = null

    @Volatile
    private var currentProcessNameMethod: Method? = null

    @Volatile
    private var loadedApkClass: Class<*>? = null

    @Volatile
    private var contextImplClass: Class<*>? = null

    fun declaredMethods(clazz: Class<*>): Array<Method> {
        return declaredMethodCache.getOrPut(clazz) { clazz.declaredMethods }
    }

    fun publicMethods(clazz: Class<*>): Array<Method> {
        return publicMethodCache.getOrPut(clazz) { clazz.methods }
    }

    fun exactMethod(clazz: Class<*>, name: String, parameterTypes: List<Class<*>>): Method {
        val key = MethodLookupKey(clazz, name, parameterTypes)
        return exactMethodCache.getOrPut(key) {
            clazz.getDeclaredMethod(name, *parameterTypes.toTypedArray())
        }
    }

    fun declaredConstructors(clazz: Class<*>): Array<out Constructor<*>> {
        return declaredConstructorCache.getOrPut(clazz) { clazz.declaredConstructors }
    }

    fun declaredField(clazz: Class<*>, name: String): Field {
        val key = FieldLookupKey(clazz, name)
        return declaredFieldCache.getOrPut(key) {
            clazz.getDeclaredField(name).apply { isAccessible = true }
        }
    }

    fun loadedApkClass(): Class<*> {
        return frameworkClass("android.app.LoadedApk", loadedApkClass) { loadedApkClass = it }
    }

    fun contextImplClass(): Class<*> {
        return frameworkClass("android.app.ContextImpl", contextImplClass) { contextImplClass = it }
    }

    fun currentApplication(): Application? {
        val method = currentApplicationMethod ?: synchronized(this) {
            currentApplicationMethod ?: activityThreadClass()
                .getDeclaredMethod("currentApplication")
                .apply { isAccessible = true }
                .also { currentApplicationMethod = it }
        }
        return method.invoke(null) as? Application
    }

    fun currentProcessName(): String? {
        val method = currentProcessNameMethod ?: synchronized(this) {
            currentProcessNameMethod ?: activityThreadClass()
                .getDeclaredMethod("currentProcessName")
                .apply { isAccessible = true }
                .also { currentProcessNameMethod = it }
        }
        return method.invoke(null) as? String
    }

    private fun activityThreadClass(): Class<*> {
        activityThreadClass?.let { return it }
        return synchronized(this) {
            activityThreadClass ?: Class.forName("android.app.ActivityThread")
                .also { activityThreadClass = it }
        }
    }

    private fun frameworkClass(name: String, cached: Class<*>?, update: (Class<*>) -> Unit): Class<*> {
        cached?.let { return it }
        return synchronized(this) {
            cached ?: Class.forName(name).also(update)
        }
    }

    private data class MethodLookupKey(
        val clazz: Class<*>,
        val name: String,
        val parameterTypes: List<Class<*>>
    )

    private data class FieldLookupKey(
        val clazz: Class<*>,
        val name: String
    )
}
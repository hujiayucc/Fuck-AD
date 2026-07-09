package com.hujiayucc.hook.hooker.util

import android.content.SharedPreferences
import java.io.File
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap
import org.json.JSONArray
import org.json.JSONObject

object DexKitMethodCache {
    private const val PREF_KEY = "dexKitMethodCache"
    private const val MAX_METHOD_CACHE_SIZE = 64

    @Volatile
    private var rawCache: String? = null

    @Volatile
    private var rootCache: JSONObject = JSONObject()

    private val methodCache = ConcurrentHashMap<String, Method>()

    fun get(
        prefs: SharedPreferences,
        packageName: String,
        apkPath: String,
        queryId: String,
        classLoader: ClassLoader?
    ): Method? {
        val key = cacheKey(packageName, apkPath, queryId)
        val entry = entry(prefs, key) ?: return null
        if (!entry.matches(apkPath)) return null
        return methodForEntry(entry, classLoader, key)
    }

    fun put(
        prefs: SharedPreferences,
        packageName: String,
        apkPath: String,
        queryId: String,
        method: Method
    ) {
        val root = JSONObject(root(prefs).toString())
        val key = cacheKey(packageName, apkPath, queryId)
        val entry = MethodEntry.from(apkPath, method)
        root.put(key, entry.toJson())
        prefs.edit().putString(PREF_KEY, root.toString()).apply()
        synchronized(this) {
            rawCache = null
            rootCache = JSONObject()
        }
        cacheMethod(key, method.declaringClass.classLoader, entry, method)
    }

    private fun entry(prefs: SharedPreferences, key: String): MethodEntry? {
        return root(prefs).optJSONObject(key)?.let { MethodEntry.fromJson(it) }
    }

    private fun root(prefs: SharedPreferences): JSONObject {
        val json = prefs.getString(PREF_KEY, "").orEmpty()
        rawCache?.let { cachedRaw ->
            if (cachedRaw == json) return rootCache
        }
        return synchronized(this) {
            if (rawCache == json) {
                rootCache
            } else {
                parseRoot(json).also { parsed ->
                    rawCache = json
                    rootCache = parsed
                }
            }
        }
    }

    private fun parseRoot(json: String): JSONObject {
        if (json.isBlank()) return JSONObject()
        return runCatching { JSONObject(json) }.getOrDefault(JSONObject())
    }

    private fun cacheKey(packageName: String, apkPath: String, queryId: String): String {
        return "$packageName|${File(apkPath).name}|$queryId"
    }

    private fun methodCacheKey(key: String, loader: ClassLoader, entry: MethodEntry): String {
        return "$key|${System.identityHashCode(loader)}|${entry.apkLength}|${entry.apkLastModified}|${entry.className}.${entry.methodName}"
    }

    private fun cacheMethod(key: String, loader: ClassLoader?, entry: MethodEntry, method: Method) {
        loader ?: return
        if (methodCache.size >= MAX_METHOD_CACHE_SIZE) methodCache.clear()
        methodCache[methodCacheKey(key, loader, entry)] = method
    }

    private fun methodForEntry(entry: MethodEntry, classLoader: ClassLoader?, key: String): Method? {
        val loader = classLoader ?: return null
        val methodKey = methodCacheKey(key, loader, entry)
        methodCache[methodKey]?.let { return it }
        return runCatching {
            val clazz = loader.loadClass(entry.className)
            clazz.declaredMethods.firstOrNull { method ->
                method.name == entry.methodName &&
                    method.returnType.name == entry.returnTypeName &&
                    method.parameterTypes.map { it.name } == entry.parameterTypeNames
            }?.apply { isAccessible = true }
                ?.also { method -> cacheMethod(key, loader, entry, method) }
        }.getOrNull()
    }

    private data class MethodEntry(
        val apkPath: String,
        val apkLength: Long,
        val apkLastModified: Long,
        val className: String,
        val methodName: String,
        val returnTypeName: String,
        val parameterTypeNames: List<String>
    ) {
        fun matches(apkPath: String): Boolean {
            val apk = File(apkPath)
            return this.apkPath == apkPath &&
                apk.isFile &&
                apk.length() == apkLength &&
                apk.lastModified() == apkLastModified
        }

        fun toJson(): JSONObject {
            return JSONObject().apply {
                put("apkPath", apkPath)
                put("apkLength", apkLength)
                put("apkLastModified", apkLastModified)
                put("className", className)
                put("methodName", methodName)
                put("returnTypeName", returnTypeName)
                put("parameterTypeNames", JSONArray(parameterTypeNames))
            }
        }

        companion object {
            fun from(apkPath: String, method: Method): MethodEntry {
                val apk = File(apkPath)
                return MethodEntry(
                    apkPath = apkPath,
                    apkLength = apk.length(),
                    apkLastModified = apk.lastModified(),
                    className = method.declaringClass.name,
                    methodName = method.name,
                    returnTypeName = method.returnType.name,
                    parameterTypeNames = method.parameterTypes.map { it.name }
                )
            }

            fun fromJson(json: JSONObject): MethodEntry? {
                val apkPath = json.optString("apkPath")
                val className = json.optString("className")
                val methodName = json.optString("methodName")
                val returnTypeName = json.optString("returnTypeName")
                if (apkPath.isBlank() || className.isBlank() || methodName.isBlank() || returnTypeName.isBlank()) return null
                return MethodEntry(
                    apkPath = apkPath,
                    apkLength = json.optLong("apkLength", -1L),
                    apkLastModified = json.optLong("apkLastModified", -1L),
                    className = className,
                    methodName = methodName,
                    returnTypeName = returnTypeName,
                    parameterTypeNames = json.optStringList("parameterTypeNames")
                )
            }

            private fun JSONObject.optStringList(name: String): List<String> {
                val array = optJSONArray(name) ?: return emptyList()
                return (0 until array.length()).mapNotNull { index ->
                    array.optString(index).takeIf { it.isNotBlank() }
                }
            }
        }
    }
}

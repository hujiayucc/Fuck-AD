package com.hujiayucc.hook.hooker.util

import android.content.SharedPreferences
import java.io.File
import java.lang.reflect.Method
import org.json.JSONArray
import org.json.JSONObject

object DexKitMethodCache {
    private const val PREF_KEY = "dexKitMethodCache"

    fun get(
        prefs: SharedPreferences,
        packageName: String,
        apkPath: String,
        queryId: String,
        classLoader: ClassLoader?
    ): Method? {
        val entry = entry(prefs, cacheKey(packageName, apkPath, queryId)) ?: return null
        if (!entry.matches(apkPath)) return null
        return entry.toMethod(classLoader)
    }

    fun put(
        prefs: SharedPreferences,
        packageName: String,
        apkPath: String,
        queryId: String,
        method: Method
    ) {
        val root = root(prefs)
        root.put(cacheKey(packageName, apkPath, queryId), MethodEntry.from(apkPath, method).toJson())
        prefs.edit().putString(PREF_KEY, root.toString()).apply()
    }

    private fun entry(prefs: SharedPreferences, key: String): MethodEntry? {
        return root(prefs).optJSONObject(key)?.let { MethodEntry.fromJson(it) }
    }

    private fun root(prefs: SharedPreferences): JSONObject {
        val json = prefs.getString(PREF_KEY, "").orEmpty()
        if (json.isBlank()) return JSONObject()
        return runCatching { JSONObject(json) }.getOrDefault(JSONObject())
    }

    private fun cacheKey(packageName: String, apkPath: String, queryId: String): String {
        return "$packageName|${File(apkPath).name}|$queryId"
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

        fun toMethod(classLoader: ClassLoader?): Method? {
            val loader = classLoader ?: return null
            return runCatching {
                val clazz = loader.loadClass(className)
                clazz.declaredMethods.firstOrNull { method ->
                    method.name == methodName &&
                        method.returnType.name == returnTypeName &&
                        method.parameterTypes.map { it.name } == parameterTypeNames
                }?.apply { isAccessible = true }
            }.getOrNull()
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

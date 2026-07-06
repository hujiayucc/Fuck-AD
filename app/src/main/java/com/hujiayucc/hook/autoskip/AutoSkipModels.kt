package com.hujiayucc.hook.autoskip

import android.graphics.Rect
import org.json.JSONArray
import org.json.JSONObject

enum class AutoSkipRuleSource {
    BUILTIN,
    SUBSCRIPTION,
    LOCAL
}

enum class AutoSkipTapStrategy {
    CENTER,
    TOP_RIGHT,
    BOTTOM_RIGHT,
    CUSTOM_RATIO,
    PROBE
}

enum class AutoSkipClickExecutorType {
    ACCESSIBILITY_GESTURE,
    SHIZUKU_INPUT,
    ROOT_INPUT,
    ACCESSIBILITY_ACTION
}

data class AutoSkipRule(
    val id: String,
    val name: String,
    val enabled: Boolean,
    val packageName: String,
    val activity: String,
    val priority: Int,
    val cooldownMs: Long,
    val delayMs: Long,
    val match: AutoSkipMatch,
    val action: AutoSkipAction,
    val source: AutoSkipRuleSource,
    val sourceId: String = "builtin"
) {
    fun appliesTo(packageName: String, className: String?): Boolean {
        val packageMatches = this.packageName == "*" || this.packageName == packageName
        val activityValues = activity.split(',', '|', ';', '\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() && it != "*" }
        val activityMatches = activity == "*" || activityValues.isEmpty() || activityValues.any { value ->
            className.orEmpty().contains(value)
        }
        return enabled && packageMatches && activityMatches
    }

    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("name", name)
            put("enabled", enabled)
            put("packageName", packageName)
            put("activity", activity)
            put("priority", priority)
            put("cooldownMs", cooldownMs)
            put("delayMs", delayMs)
            put("match", match.toJson())
            put("action", action.toJson())
            put("source", source.name.lowercase())
            put("sourceId", sourceId)
        }
    }

    companion object {
        fun fromJson(obj: JSONObject, fallbackSource: AutoSkipRuleSource, fallbackSourceId: String): AutoSkipRule? {
            val id = obj.optString("id")
            val name = obj.optString("name", id)
            if (id.isBlank() || name.isBlank()) return null
            val source = sourceFromString(obj.optString("source")) ?: fallbackSource
            return AutoSkipRule(
                id = id,
                name = name,
                enabled = obj.optBoolean("enabled", true),
                packageName = obj.optString("packageName", "*"),
                activity = obj.optString("activity", "*"),
                priority = obj.optInt("priority", 0),
                cooldownMs = obj.optLong("cooldownMs", 3000L).coerceAtLeast(500L),
                delayMs = obj.optLong("delayMs", 0L).coerceIn(0L, 5000L),
                match = AutoSkipMatch.fromJson(obj.optJSONObject("match") ?: JSONObject()),
                action = AutoSkipAction.fromJson(obj.optJSONObject("action") ?: JSONObject()),
                source = source,
                sourceId = obj.optString("sourceId", fallbackSourceId)
            )
        }

        private fun sourceFromString(value: String): AutoSkipRuleSource? {
            return when (value.lowercase()) {
                "builtin" -> AutoSkipRuleSource.BUILTIN
                "subscription" -> AutoSkipRuleSource.SUBSCRIPTION
                "local" -> AutoSkipRuleSource.LOCAL
                else -> null
            }
        }
    }
}

data class AutoSkipMatch(
    val text: List<String> = emptyList(),
    val desc: List<String> = emptyList(),
    val resourceId: List<String> = emptyList(),
    val className: List<String> = emptyList(),
    val excludeText: List<String> = emptyList(),
    val excludeDesc: List<String> = emptyList(),
    val excludeResourceId: List<String> = emptyList(),
    val gkdSelectors: List<String> = emptyList(),
    val excludeGkdSelectors: List<String> = emptyList(),
    val visible: Boolean = true,
    val region: AutoSkipRegion? = null
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("text", JSONArray(text))
            put("desc", JSONArray(desc))
            put("resourceId", JSONArray(resourceId))
            put("className", JSONArray(className))
            put("excludeText", JSONArray(excludeText))
            put("excludeDesc", JSONArray(excludeDesc))
            put("excludeResourceId", JSONArray(excludeResourceId))
            put("gkdSelectors", JSONArray(gkdSelectors))
            put("excludeGkdSelectors", JSONArray(excludeGkdSelectors))
            put("visible", visible)
            region?.let { put("bounds", it.toJson()) }
        }
    }

    companion object {
        fun fromJson(obj: JSONObject): AutoSkipMatch {
            return AutoSkipMatch(
                text = obj.optStringList("text"),
                desc = obj.optStringList("desc"),
                resourceId = obj.optStringList("resourceId"),
                className = obj.optStringList("className"),
                excludeText = obj.optStringList("excludeText"),
                excludeDesc = obj.optStringList("excludeDesc"),
                excludeResourceId = obj.optStringList("excludeResourceId"),
                gkdSelectors = obj.optStringList("gkdSelectors") + obj.optStringList("selector") + obj.optStringList("matches"),
                excludeGkdSelectors = obj.optStringList("excludeGkdSelectors") + obj.optStringList("excludeSelector") + obj.optStringList("excludeMatches"),
                visible = obj.optBoolean("visible", true),
                region = obj.optJSONObject("bounds")?.let { AutoSkipRegion.fromJson(it) }
            )
        }
    }
}

data class AutoSkipAction(
    val tapStrategy: AutoSkipTapStrategy = AutoSkipTapStrategy.CENTER,
    val customXRatio: Float = 0.5f,
    val customYRatio: Float = 0.5f,
    val fallbackExecutors: List<AutoSkipClickExecutorType> = defaultExecutors()
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("type", "tap")
            put("executor", "auto")
            put("tapStrategy", tapStrategy.name.lowercase())
            put("customXRatio", customXRatio.toDouble())
            put("customYRatio", customYRatio.toDouble())
            put("fallbackExecutors", JSONArray(fallbackExecutors.map { it.name.lowercase() }))
        }
    }

    companion object {
        fun defaultExecutors(): List<AutoSkipClickExecutorType> {
            return listOf(
                AutoSkipClickExecutorType.SHIZUKU_INPUT,
                AutoSkipClickExecutorType.ROOT_INPUT,
                AutoSkipClickExecutorType.ACCESSIBILITY_GESTURE,
                AutoSkipClickExecutorType.ACCESSIBILITY_ACTION
            )
        }

        fun fromJson(obj: JSONObject): AutoSkipAction {
            val fallback = obj.optStringList("fallbackExecutors")
                .mapNotNull { executorFromString(it) }
                .ifEmpty { defaultExecutors() }
            return AutoSkipAction(
                tapStrategy = tapStrategyFromString(obj.optString("tapStrategy")) ?: AutoSkipTapStrategy.CENTER,
                customXRatio = obj.optDouble("customXRatio", 0.5).toFloat().coerceIn(0f, 1f),
                customYRatio = obj.optDouble("customYRatio", 0.5).toFloat().coerceIn(0f, 1f),
                fallbackExecutors = fallback
            )
        }

        private fun tapStrategyFromString(value: String): AutoSkipTapStrategy? {
            return when (value.lowercase()) {
                "center" -> AutoSkipTapStrategy.CENTER
                "topright", "top_right" -> AutoSkipTapStrategy.TOP_RIGHT
                "bottomright", "bottom_right" -> AutoSkipTapStrategy.BOTTOM_RIGHT
                "customratio", "custom_ratio" -> AutoSkipTapStrategy.CUSTOM_RATIO
                "probe" -> AutoSkipTapStrategy.PROBE
                else -> null
            }
        }

        private fun executorFromString(value: String): AutoSkipClickExecutorType? {
            return when (value.lowercase()) {
                "accessibility_gesture" -> AutoSkipClickExecutorType.ACCESSIBILITY_GESTURE
                "shizuku_input" -> AutoSkipClickExecutorType.SHIZUKU_INPUT
                "root_input" -> AutoSkipClickExecutorType.ROOT_INPUT
                "accessibility_action" -> AutoSkipClickExecutorType.ACCESSIBILITY_ACTION
                else -> null
            }
        }
    }
}

data class AutoSkipRegion(
    val name: String = "any"
) {
    fun contains(bounds: Rect, screenWidth: Int, screenHeight: Int): Boolean {
        if (bounds.isEmpty || screenWidth <= 0 || screenHeight <= 0) return false
        return when (name.lowercase()) {
            "topright", "top_right" -> bounds.centerX() >= screenWidth * 0.45f && bounds.centerY() <= screenHeight * 0.45f
            "bottomright", "bottom_right" -> bounds.centerX() >= screenWidth * 0.45f && bounds.centerY() >= screenHeight * 0.45f
            "top" -> bounds.centerY() <= screenHeight * 0.45f
            "bottom" -> bounds.centerY() >= screenHeight * 0.45f
            else -> true
        }
    }

    fun toJson(): JSONObject {
        return JSONObject().put("region", name)
    }

    companion object {
        fun fromJson(obj: JSONObject): AutoSkipRegion {
            return AutoSkipRegion(obj.optString("region", "any"))
        }
    }
}

data class AutoSkipRuleSourceConfig(
    val id: String,
    val url: String,
    val enabled: Boolean,
    val lastUpdateTime: Long,
    val lastResult: String,
    val cacheFile: String = ""
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("url", url)
            put("enabled", enabled)
            put("lastUpdateTime", lastUpdateTime)
            put("lastResult", lastResult)
            put("cacheFile", cacheFile)
        }
    }

    companion object {
        fun fromJson(obj: JSONObject): AutoSkipRuleSourceConfig? {
            val id = obj.optString("id")
            val url = obj.optString("url")
            if (id.isBlank() || url.isBlank()) return null
            return AutoSkipRuleSourceConfig(
                id = id,
                url = url,
                enabled = obj.optBoolean("enabled", true),
                lastUpdateTime = obj.optLong("lastUpdateTime", 0L),
                lastResult = obj.optString("lastResult", ""),
                cacheFile = obj.optString("cacheFile", "")
            )
        }
    }
}

private fun JSONObject.optStringList(key: String): List<String> {
    val value = opt(key) ?: return emptyList()
    return when (value) {
        is JSONArray -> {
            val result = ArrayList<String>()
            for (index in 0 until value.length()) {
                val item = value.optString(index).trim()
                if (item.isNotEmpty()) result.add(item)
            }
            result
        }
        is String -> listOf(value.trim()).filter { it.isNotEmpty() }
        is Number -> listOf(value.toString())
        is Boolean -> listOf(value.toString())
        else -> emptyList()
    }
}

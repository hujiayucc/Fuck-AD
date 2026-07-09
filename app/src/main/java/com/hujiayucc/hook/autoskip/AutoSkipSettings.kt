package com.hujiayucc.hook.autoskip

import android.content.Context
import com.hujiayucc.hook.data.Data.prefsBridge
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import org.json.JSONArray
import org.json.JSONObject

object AutoSkipSettings {
    private const val KEY_ENABLED = "autoSkipEnabled"
    private const val KEY_USE_SHIZUKU_INPUT = "autoSkipUseShizukuInput"
    private const val KEY_USE_ROOT_INPUT = "autoSkipUseRootInput"
    private const val KEY_ENABLED_PACKAGES = "autoSkipEnabledPackages"
    private const val KEY_DISABLED_RULE_IDS = "autoSkipDisabledRuleIds"
    private const val KEY_SOURCES = "autoSkipRuleSources"
    private const val KEY_SUBSCRIPTION_RULES = "autoSkipSubscriptionRules"
    private const val KEY_LOCAL_RULES = "autoSkipLocalRules"
    private const val KEY_HIT_LOGS = "autoSkipHitLogs"
    private const val KEY_LAST_UPDATE_TIME = "autoSkipLastUpdateTime"

    @Volatile
    private var enabledPackagesRawCache: String? = null

    @Volatile
    private var enabledPackagesCache: Set<String> = emptySet()

    @Volatile
    private var disabledRuleIdsRawCache: String? = null

    @Volatile
    private var disabledRuleIdsCache: Set<String> = emptySet()

    private val hitLogLock = Any()
    private val hitLogExecutor = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "AutoSkipHitLogWriter").apply { isDaemon = true }
    }
    private val pendingHitLogs = ArrayList<AutoSkipHitLog>()
    private var pendingHitLogContext: Context? = null
    private var scheduledHitLogFlush: ScheduledFuture<*>? = null
    private var hitLogGeneration = 0L

    fun isEnabled(context: Context): Boolean {
        return context.prefsBridge.getBoolean(KEY_ENABLED, false)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        context.prefsBridge.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun useShizukuInput(context: Context): Boolean {
        return context.prefsBridge.getBoolean(KEY_USE_SHIZUKU_INPUT, false)
    }

    fun setUseShizukuInput(context: Context, enabled: Boolean) {
        context.prefsBridge.edit().putBoolean(KEY_USE_SHIZUKU_INPUT, enabled).apply()
    }

    fun useRootInput(context: Context): Boolean {
        return context.prefsBridge.getBoolean(KEY_USE_ROOT_INPUT, false)
    }

    fun setUseRootInput(context: Context, enabled: Boolean) {
        context.prefsBridge.edit().putBoolean(KEY_USE_ROOT_INPUT, enabled).apply()
    }

    fun enabledPackages(context: Context): Set<String> {
        val raw = context.prefsBridge.getString(KEY_ENABLED_PACKAGES, "").orEmpty()
        enabledPackagesRawCache?.let { cachedRaw ->
            if (cachedRaw == raw) return enabledPackagesCache
        }
        return synchronized(this) {
            if (enabledPackagesRawCache == raw) {
                enabledPackagesCache
            } else {
                readStringSet(raw).also { packages ->
                    enabledPackagesRawCache = raw
                    enabledPackagesCache = packages
                }
            }
        }
    }

    fun isAppEnabled(context: Context, packageName: String): Boolean {
        return enabledPackages(context).contains(packageName)
    }

    fun setAppEnabled(context: Context, packageName: String, enabled: Boolean) {
        val packages = enabledPackages(context).toMutableSet()
        if (enabled) packages.add(packageName) else packages.remove(packageName)
        context.prefsBridge.edit().putString(KEY_ENABLED_PACKAGES, JSONArray(packages.sorted()).toString()).apply()
    }

    fun addEnabledPackages(context: Context, packageNames: Iterable<String>): Int {
        val packages = enabledPackages(context).toMutableSet()
        val beforeSize = packages.size
        packageNames.map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach { packages.add(it) }
        if (packages.size != beforeSize) {
            context.prefsBridge.edit().putString(KEY_ENABLED_PACKAGES, JSONArray(packages.sorted()).toString()).apply()
        }
        return packages.size - beforeSize
    }

    fun disabledRuleIds(context: Context): Set<String> {
        val raw = context.prefsBridge.getString(KEY_DISABLED_RULE_IDS, "").orEmpty()
        disabledRuleIdsRawCache?.let { cachedRaw ->
            if (cachedRaw == raw) return disabledRuleIdsCache
        }
        return synchronized(this) {
            if (disabledRuleIdsRawCache == raw) {
                disabledRuleIdsCache
            } else {
                readStringSet(raw).also { ruleIds ->
                    disabledRuleIdsRawCache = raw
                    disabledRuleIdsCache = ruleIds
                }
            }
        }
    }

    fun isRuleEnabled(context: Context, ruleId: String): Boolean {
        return !disabledRuleIds(context).contains(ruleId)
    }

    fun setRuleEnabled(context: Context, ruleId: String, enabled: Boolean) {
        val disabled = disabledRuleIds(context).toMutableSet()
        if (enabled) disabled.remove(ruleId) else disabled.add(ruleId)
        context.prefsBridge.edit().putString(KEY_DISABLED_RULE_IDS, JSONArray(disabled.sorted()).toString()).apply()
    }

    fun ruleDataVersion(context: Context): Int {
        val prefs = context.prefsBridge
        var result = 1
        result = 31 * result + prefs.getString(KEY_DISABLED_RULE_IDS, "").orEmpty().hashCode()
        result = 31 * result + prefs.getString(KEY_SOURCES, "").orEmpty().hashCode()
        result = 31 * result + prefs.getString(KEY_SUBSCRIPTION_RULES, "").orEmpty().hashCode()
        result = 31 * result + prefs.getString(KEY_LOCAL_RULES, "").orEmpty().hashCode()
        result = 31 * result + prefs.getLong(KEY_LAST_UPDATE_TIME, 0L).toString().hashCode()
        return result
    }

    fun sources(context: Context): List<AutoSkipRuleSourceConfig> {
        val json = context.prefsBridge.getString(KEY_SOURCES, "").orEmpty()
        if (json.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(json)
            val loaded = ArrayList<AutoSkipRuleSourceConfig>().apply {
                for (index in 0 until arr.length()) {
                    AutoSkipRuleSourceConfig.fromJson(arr.optJSONObject(index) ?: JSONObject())?.let { add(it) }
                }
            }.toList()
            val normalized = normalizeSources(loaded)
            if (normalized != loaded) saveSources(context, normalized)
            normalized
        }.getOrDefault(emptyList())
    }

    fun saveSources(context: Context, sources: List<AutoSkipRuleSourceConfig>) {
        val arr = JSONArray()
        normalizeSources(sources).forEach { arr.put(it.toJson()) }
        context.prefsBridge.edit().putString(KEY_SOURCES, arr.toString()).apply()
    }

    fun subscriptionRules(context: Context): Map<String, List<AutoSkipRule>> {
        val sources = sources(context)
        return if (sources.isEmpty()) legacySubscriptionRules(context) else subscriptionRules(context, sources)
    }

    fun subscriptionRules(context: Context, sources: List<AutoSkipRuleSourceConfig>): Map<String, List<AutoSkipRule>> {
        val legacyRules = legacySubscriptionRules(context)
        val result = LinkedHashMap<String, List<AutoSkipRule>>()
        normalizeSources(sources).forEach { source ->
            val cacheFile = source.cacheFile
            val fileRules = parseRuleArrayOrNull(readSourceRulesFile(context, cacheFile), AutoSkipRuleSource.SUBSCRIPTION, source.id)
            val rules = fileRules ?: legacyRules[source.id].orEmpty()
            if (rules.isNotEmpty()) {
                result[source.id] = rules
                if (fileRules == null) saveSourceRulesFile(context, cacheFile, rules)
            }
        }
        return result
    }

    fun saveSubscriptionRules(context: Context, rulesBySource: Map<String, List<AutoSkipRule>>) {
        val sources = sources(context)
        if (sources.isNotEmpty()) {
            saveSubscriptionRules(context, sources, rulesBySource)
            return
        }
        saveLegacySubscriptionRules(context, rulesBySource)
    }

    fun saveSubscriptionRules(context: Context, sources: List<AutoSkipRuleSourceConfig>, rulesBySource: Map<String, List<AutoSkipRule>>) {
        val legacyObj = JSONObject()
        normalizeSources(sources).forEach { source ->
            val rules = rulesBySource[source.id].orEmpty()
            if (rules.isEmpty()) {
                deleteSourceRulesFile(context, source.cacheFile)
            } else {
                saveSourceRulesFile(context, source.cacheFile, rules)
            }
            val arr = JSONArray()
            rules.forEach { arr.put(it.toJson()) }
            legacyObj.put(source.id, arr)
        }
        context.prefsBridge.edit().putString(KEY_SUBSCRIPTION_RULES, legacyObj.toString()).apply()
    }

    fun deleteSubscriptionRulesCache(context: Context, source: AutoSkipRuleSourceConfig) {
        val cacheFile = normalizedCacheFile(source.cacheFile).ifBlank { cacheFileNameForSource(source) }
        deleteSourceRulesFile(context, cacheFile)
    }

    fun clearSubscriptionRulesCache(context: Context, sources: List<AutoSkipRuleSourceConfig>) {
        normalizeSources(sources).forEach { deleteSubscriptionRulesCache(context, it) }
        saveLegacySubscriptionRules(context, emptyMap())
    }

    fun localRules(context: Context): List<AutoSkipRule> {
        val json = context.prefsBridge.getString(KEY_LOCAL_RULES, "").orEmpty()
        if (json.isBlank()) return emptyList()
        return runCatching {
            parseRuleArray(JSONArray(json), AutoSkipRuleSource.LOCAL, "local")
        }.getOrDefault(emptyList())
    }

    fun saveLocalRules(context: Context, rules: List<AutoSkipRule>) {
        val arr = JSONArray()
        rules.forEach { arr.put(it.toJson()) }
        context.prefsBridge.edit().putString(KEY_LOCAL_RULES, arr.toString()).apply()
    }

    fun lastUpdateTime(context: Context): Long {
        return context.prefsBridge.getLong(KEY_LAST_UPDATE_TIME, 0L)
    }

    fun setLastUpdateTime(context: Context, time: Long) {
        context.prefsBridge.edit().putLong(KEY_LAST_UPDATE_TIME, time).apply()
    }

    fun hitLogs(context: Context): List<AutoSkipHitLog> {
        val stored = readHitLogs(context)
        val pending = synchronized(hitLogLock) { pendingHitLogs.toList() }
        if (pending.isEmpty()) return stored
        return (pending.asReversed() + stored).take(MAX_LOG_COUNT)
    }

    fun recordHit(context: Context, log: AutoSkipHitLog) {
        synchronized(hitLogLock) {
            pendingHitLogs.add(log)
            if (pendingHitLogs.size > MAX_PENDING_LOG_COUNT) {
                pendingHitLogs.removeAt(0)
            }
            pendingHitLogContext = context.applicationContext
            if (scheduledHitLogFlush?.isDone != false) {
                val generation = hitLogGeneration
                scheduledHitLogFlush = hitLogExecutor.schedule(
                    { flushPendingHitLogs(generation) },
                    HIT_LOG_FLUSH_DELAY_MS,
                    TimeUnit.MILLISECONDS
                )
            }
        }
    }

    fun clearHitLogs(context: Context) {
        synchronized(hitLogLock) {
            pendingHitLogs.clear()
            hitLogGeneration += 1
            scheduledHitLogFlush?.cancel(false)
            scheduledHitLogFlush = null
            pendingHitLogContext = null
        }
        context.prefsBridge.edit().remove(KEY_HIT_LOGS).apply()
    }

    private fun flushPendingHitLogs(expectedGeneration: Long) {
        val context: Context
        val pending: List<AutoSkipHitLog>
        synchronized(hitLogLock) {
            if (expectedGeneration != hitLogGeneration) return
            context = pendingHitLogContext ?: return
            if (pendingHitLogs.isEmpty()) return
            pending = pendingHitLogs.toList()
            pendingHitLogs.clear()
            scheduledHitLogFlush = null
        }
        val logs = (pending.asReversed() + readHitLogs(context)).take(MAX_LOG_COUNT)
        synchronized(hitLogLock) {
            if (expectedGeneration != hitLogGeneration) return
            writeHitLogs(context, logs)
        }
    }

    private fun readHitLogs(context: Context): List<AutoSkipHitLog> {
        val json = context.prefsBridge.getString(KEY_HIT_LOGS, "").orEmpty()
        if (json.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(json)
            ArrayList<AutoSkipHitLog>().apply {
                for (index in 0 until arr.length()) {
                    AutoSkipHitLog.fromJson(arr.optJSONObject(index) ?: JSONObject())?.let { add(it) }
                }
            }.toList()
        }.getOrDefault(emptyList())
    }

    private fun writeHitLogs(context: Context, logs: List<AutoSkipHitLog>) {
        val arr = JSONArray()
        logs.take(MAX_LOG_COUNT).forEach { arr.put(it.toJson()) }
        context.prefsBridge.edit().putString(KEY_HIT_LOGS, arr.toString()).apply()
    }

    private fun legacySubscriptionRules(context: Context): Map<String, List<AutoSkipRule>> {
        val prefsJson = context.prefsBridge.getString(KEY_SUBSCRIPTION_RULES, "").orEmpty()
        val prefsRules = parseSubscriptionRulesOrNull(prefsJson)
        val fileJson = readSubscriptionRulesFile(context)
        val fileRules = parseSubscriptionRulesOrNull(fileJson)
        return when {
            prefsRules != null && prefsRules.isNotEmpty() -> {
                ensureSubscriptionRulesFile(context, prefsJson)
                prefsRules
            }
            fileRules != null && fileRules.isNotEmpty() -> fileRules
            prefsRules != null -> prefsRules
            fileRules != null -> fileRules
            else -> emptyMap()
        }
    }

    private fun saveLegacySubscriptionRules(context: Context, rulesBySource: Map<String, List<AutoSkipRule>>) {
        val obj = JSONObject()
        rulesBySource.forEach { (sourceId, rules) ->
            val arr = JSONArray()
            rules.forEach { arr.put(it.toJson()) }
            obj.put(sourceId, arr)
        }
        val json = obj.toString()
        context.prefsBridge.edit().putString(KEY_SUBSCRIPTION_RULES, json).apply()
        saveSubscriptionRulesFile(context, json)
    }

    private fun parseSubscriptionRulesOrNull(json: String?): Map<String, List<AutoSkipRule>>? {
        if (json.isNullOrBlank()) return null
        return runCatching<Map<String, List<AutoSkipRule>>> {
            val obj = JSONObject(json)
            val result = LinkedHashMap<String, List<AutoSkipRule>>()
            obj.keys().forEach { sourceId ->
                val arr = obj.optJSONArray(sourceId) ?: JSONArray()
                result[sourceId] = parseRuleArray(arr, AutoSkipRuleSource.SUBSCRIPTION, sourceId)
            }
            result
        }.getOrNull()
    }

    private fun parseRuleArrayOrNull(json: String?, source: AutoSkipRuleSource, sourceId: String): List<AutoSkipRule>? {
        if (json.isNullOrBlank()) return null
        return runCatching { parseRuleArray(JSONArray(json), source, sourceId) }.getOrNull()
    }

    private fun readSubscriptionRulesFile(context: Context): String {
        return runCatching { subscriptionRulesFile(context).takeIf { it.isFile }?.readText().orEmpty() }.getOrDefault("")
    }

    private fun ensureSubscriptionRulesFile(context: Context, json: String) {
        runCatching {
            val file = subscriptionRulesFile(context)
            if (file.isFile && file.length() > 0L) return@runCatching
            saveSubscriptionRulesFile(context, json)
        }
    }

    private fun saveSubscriptionRulesFile(context: Context, json: String) {
        runCatching {
            val file = subscriptionRulesFile(context)
            if (json == "{}") {
                if (file.exists()) file.delete()
                return@runCatching
            }
            file.parentFile?.mkdirs()
            file.writeText(json)
        }
    }

    private fun subscriptionRulesFile(context: Context): File {
        return File(File(context.applicationContext.filesDir, CACHE_DIR), SUBSCRIPTION_RULES_FILE)
    }

    private fun readSourceRulesFile(context: Context, cacheFile: String): String {
        return runCatching { sourceRulesFile(context, cacheFile)?.takeIf { it.isFile }?.readText().orEmpty() }.getOrDefault("")
    }

    private fun saveSourceRulesFile(context: Context, cacheFile: String, rules: List<AutoSkipRule>) {
        runCatching {
            val file = sourceRulesFile(context, cacheFile) ?: return@runCatching
            val arr = JSONArray()
            rules.forEach { arr.put(it.toJson()) }
            file.parentFile?.mkdirs()
            file.writeText(arr.toString())
        }
    }

    private fun deleteSourceRulesFile(context: Context, cacheFile: String) {
        runCatching { sourceRulesFile(context, cacheFile)?.takeIf { it.exists() }?.delete() }
    }

    private fun sourceRulesFile(context: Context, cacheFile: String): File? {
        val fileName = normalizedCacheFile(cacheFile)
        if (fileName.isBlank()) return null
        return File(File(context.applicationContext.filesDir, CACHE_DIR), fileName)
    }

    private fun normalizeSources(sources: List<AutoSkipRuleSourceConfig>): List<AutoSkipRuleSourceConfig> {
        return sources.map { source ->
            val cacheFile = normalizedCacheFile(source.cacheFile).ifBlank { cacheFileNameForSource(source) }
            if (source.cacheFile == cacheFile) source else source.copy(cacheFile = cacheFile)
        }
    }

    private fun normalizedCacheFile(value: String): String {
        val rawName = value.substringAfterLast('/').substringAfterLast('\\').trim()
        if (rawName.isBlank()) return ""
        val safeName = rawName.map { char ->
            when {
                char in 'a'..'z' -> char
                char in 'A'..'Z' -> char
                char in '0'..'9' -> char
                char == '.' || char == '_' || char == '-' -> char
                else -> '_'
            }
        }.joinToString("").trim('.', '_', '-').take(160)
        if (safeName.isBlank()) return ""
        return if (safeName.endsWith(".json", ignoreCase = true)) safeName else "$safeName.json"
    }

    private fun cacheFileNameForSource(source: AutoSkipRuleSourceConfig): String {
        return normalizedCacheFile(source.id).ifBlank { "source_${Integer.toHexString(source.url.hashCode())}.json" }
    }

    private fun parseRuleArray(
        array: JSONArray,
        source: AutoSkipRuleSource,
        sourceId: String
    ): List<AutoSkipRule> {
        val rules = ArrayList<AutoSkipRule>()
        for (index in 0 until array.length()) {
            AutoSkipRule.fromJson(array.optJSONObject(index) ?: JSONObject(), source, sourceId)?.let { rules.add(it) }
        }
        return rules
    }

    private fun readStringSet(json: String?): Set<String> {
        if (json.isNullOrBlank()) return emptySet()
        return runCatching {
            val arr = JSONArray(json)
            LinkedHashSet<String>().apply {
                for (index in 0 until arr.length()) {
                    val value = arr.optString(index)
                    if (value.isNotBlank()) add(value)
                }
            }.toSet()
        }.getOrDefault(emptySet())
    }

    private const val MAX_LOG_COUNT = 80
    private const val MAX_PENDING_LOG_COUNT = 80
    private const val HIT_LOG_FLUSH_DELAY_MS = 750L
    private const val CACHE_DIR = "auto_skip"
    private const val SUBSCRIPTION_RULES_FILE = "subscription_rules.json"
}

data class AutoSkipHitLog(
    val time: Long,
    val packageName: String,
    val activity: String,
    val ruleId: String,
    val ruleName: String,
    val executor: String,
    val x: Int,
    val y: Int,
    val result: String
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("time", time)
            put("packageName", packageName)
            put("activity", activity)
            put("ruleId", ruleId)
            put("ruleName", ruleName)
            put("executor", executor)
            put("x", x)
            put("y", y)
            put("result", result)
        }
    }

    companion object {
        fun fromJson(obj: JSONObject): AutoSkipHitLog? {
            val ruleId = obj.optString("ruleId")
            if (ruleId.isBlank()) return null
            return AutoSkipHitLog(
                time = obj.optLong("time", 0L),
                packageName = obj.optString("packageName"),
                activity = obj.optString("activity"),
                ruleId = ruleId,
                ruleName = obj.optString("ruleName", ruleId),
                executor = obj.optString("executor"),
                x = obj.optInt("x"),
                y = obj.optInt("y"),
                result = obj.optString("result")
            )
        }
    }
}
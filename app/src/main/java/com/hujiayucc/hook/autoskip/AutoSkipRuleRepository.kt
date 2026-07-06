package com.hujiayucc.hook.autoskip

import android.content.Context
import com.hujiayucc.hook.R
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

class AutoSkipRuleRepository(private val context: Context) {
    private val appContext = context.applicationContext

    fun rules(): List<AutoSkipRule> {
        val disabled = AutoSkipSettings.disabledRuleIds(appContext)
        val sources = AutoSkipSettings.sources(appContext)
        val enabledSourceIds = sources.filter { it.enabled }.map { it.id }.toSet()
        val merged = LinkedHashMap<String, AutoSkipRule>()
        builtinRules().forEach { merged[it.id] = it }
        AutoSkipSettings.subscriptionRules(appContext, sources)
            .filterKeys { sourceId -> enabledSourceIds.contains(sourceId) }
            .values
            .flatten()
            .forEach { merged[it.id] = it }
        AutoSkipSettings.localRules(appContext).forEach { merged[it.id] = it }
        return merged.values
            .map { rule -> if (disabled.contains(rule.id)) rule.copy(enabled = false) else rule }
            .sortedWith(compareByDescending<AutoSkipRule> { it.priority }.thenBy { it.name })
    }

    fun executableRules(packageName: String, activity: String?): List<AutoSkipRule> {
        return rules().filter { rule ->
            rule.appliesTo(packageName, activity) && !isSensitivePackage(packageName) && !isSensitiveActivity(activity)
        }
    }

    fun stats(): AutoSkipRuleStats {
        return stats(rules())
    }

    fun stats(allRules: List<AutoSkipRule>): AutoSkipRuleStats {
        val sources = AutoSkipSettings.sources(appContext)
        return AutoSkipRuleStats(
            total = allRules.size,
            enabled = allRules.count { it.enabled },
            builtin = allRules.count { it.source == AutoSkipRuleSource.BUILTIN },
            subscription = allRules.count { it.source == AutoSkipRuleSource.SUBSCRIPTION },
            local = allRules.count { it.source == AutoSkipRuleSource.LOCAL },
            sourceCount = sources.size,
            lastUpdateTime = AutoSkipSettings.lastUpdateTime(appContext)
        )
    }

    fun sources(): List<AutoSkipRuleSourceConfig> {
        return AutoSkipSettings.sources(appContext)
    }

    fun addSource(url: String): AutoSkipUpdateResult {
        val normalized = url.trim()
        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            return AutoSkipUpdateResult(false, appContext.getString(R.string.auto_skip_result_invalid_url), 0, 0, 0)
        }
        val sources = AutoSkipSettings.sources(appContext).toMutableList()
        if (sources.any { it.url == normalized }) {
            return AutoSkipUpdateResult(false, appContext.getString(R.string.auto_skip_result_source_exists), 0, 0, 0)
        }
        val source = AutoSkipRuleSourceConfig(
            id = "source.${System.currentTimeMillis()}",
            url = normalized,
            enabled = true,
            lastUpdateTime = 0L,
            lastResult = appContext.getString(R.string.auto_skip_result_added)
        )
        sources.add(source)
        AutoSkipSettings.saveSources(appContext, sources)
        return AutoSkipUpdateResult(true, appContext.getString(R.string.auto_skip_result_source_added), 0, 0, 0)
    }

    fun addSourceAndUpdate(url: String): AutoSkipUpdateResult {
        val addResult = addSource(url)
        val normalized = url.trim()
        if (!addResult.success) {
            val sources = AutoSkipSettings.sources(appContext)
            val existing = sources.firstOrNull { it.url == normalized } ?: return addResult
            if (!existing.enabled) {
                AutoSkipSettings.saveSources(appContext, sources.map { source ->
                    if (source.id == existing.id) source.copy(enabled = true) else source
                })
            }
        }
        return updateSources(setOf(normalized))
    }

    fun setSourceEnabled(sourceId: String, enabled: Boolean): AutoSkipUpdateResult {
        var found = false
        val updated = AutoSkipSettings.sources(appContext).map { source ->
            if (source.id == sourceId) {
                found = true
                source.copy(enabled = enabled)
            } else {
                source
            }
        }
        if (!found) return AutoSkipUpdateResult(false, appContext.getString(R.string.auto_skip_result_source_not_found), 0, 0, 1)
        AutoSkipSettings.saveSources(appContext, updated)
        return AutoSkipUpdateResult(
            true,
            appContext.getString(if (enabled) R.string.auto_skip_result_source_enabled else R.string.auto_skip_result_source_disabled),
            0,
            0,
            0
        )
    }

    fun deleteSource(sourceId: String): AutoSkipUpdateResult {
        val sources = AutoSkipSettings.sources(appContext)
        val target = sources.firstOrNull { it.id == sourceId }
            ?: return AutoSkipUpdateResult(false, appContext.getString(R.string.auto_skip_result_source_not_found), 0, 0, 1)
        AutoSkipSettings.saveSources(appContext, sources.filterNot { it.id == sourceId })
        val rulesBySource = AutoSkipSettings.subscriptionRules(appContext, sources).toMutableMap()
        val removedRules = rulesBySource.remove(sourceId).orEmpty().size
        AutoSkipSettings.deleteSubscriptionRulesCache(appContext, target)
        AutoSkipSettings.saveSubscriptionRules(appContext, sources.filterNot { it.id == sourceId }, rulesBySource)
        AutoSkipSettings.setLastUpdateTime(appContext, System.currentTimeMillis())
        return AutoSkipUpdateResult(
            success = true,
            message = appContext.getString(R.string.auto_skip_result_source_deleted),
            added = 0,
            changed = 0,
            failed = 0,
            removed = removedRules,
            details = target.url
        )
    }

    fun updateSources(autoEnableAllForUrls: Set<String> = emptySet()): AutoSkipUpdateResult {
        val sources = AutoSkipSettings.sources(appContext)
        val urlsForFullEnable = autoEnableAllForUrls.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        val enabledSources = sources.filter { it.enabled }
        if (enabledSources.isEmpty()) {
            return AutoSkipUpdateResult(false, appContext.getString(R.string.auto_skip_result_no_enabled_source), 0, 0, 0)
        }

        val oldRules = AutoSkipSettings.subscriptionRules(appContext, sources)
        val newRules = LinkedHashMap(oldRules)
        val updatedSources = ArrayList<AutoSkipRuleSourceConfig>()
        val detailLines = ArrayList<String>()
        var added = 0
        var changed = 0
        var removed = 0
        var failed = 0
        var successful = 0
        val packagesToEnable = LinkedHashSet<String>()

        sources.forEach { source ->
            if (!source.enabled) {
                updatedSources.add(source)
                return@forEach
            }
            val result = runCatching { downloadRules(source) }
            result.onSuccess { rules ->
                val previous = oldRules[source.id].orEmpty()
                if (rules.isEmpty()) {
                    failed += 1
                    updatedSources.add(source.copy(lastUpdateTime = System.currentTimeMillis(), lastResult = appContext.getString(R.string.auto_skip_result_empty_rules)))
                    detailLines.add(appContext.getString(R.string.auto_skip_result_detail_empty_rules, compactUrl(source.url)))
                    return@onSuccess
                }
                if (previous.size >= 10 && rules.size < previous.size / 3) {
                    failed += 1
                    updatedSources.add(source.copy(lastUpdateTime = System.currentTimeMillis(), lastResult = appContext.getString(R.string.auto_skip_result_abnormal_rule_decrease)))
                    detailLines.add(appContext.getString(R.string.auto_skip_result_detail_abnormal_decrease, compactUrl(source.url), previous.size, rules.size))
                    return@onSuccess
                }
                val diff = diffRules(previous, rules)
                added += diff.added
                changed += diff.changed
                removed += diff.removed
                packagesToEnable.addAll(
                    if (urlsForFullEnable.contains(source.url)) {
                        packageNamesForAutoEnable(rules)
                    } else {
                        packagesForAutoEnable(previous, rules)
                    }
                )
                successful += 1
                newRules[source.id] = rules
                updatedSources.add(
                    source.copy(
                        lastUpdateTime = System.currentTimeMillis(),
                        lastResult = appContext.getString(R.string.auto_skip_result_source_update_detail, diff.added, diff.changed, diff.removed, rules.size)
                    )
                )
                detailLines.add(appContext.getString(R.string.auto_skip_result_detail_source_update, compactUrl(source.url), diff.added, diff.changed, diff.removed, rules.size))
            }.onFailure { error ->
                failed += 1
                updatedSources.add(source.copy(lastUpdateTime = System.currentTimeMillis(), lastResult = error.message ?: appContext.getString(R.string.auto_skip_update_failed)))
                detailLines.add(appContext.getString(R.string.auto_skip_result_detail_failed, compactUrl(source.url), error.message ?: appContext.getString(R.string.auto_skip_update_failed)))
            }
        }

        AutoSkipSettings.saveSources(appContext, updatedSources)
        if (successful > 0) {
            AutoSkipSettings.saveSubscriptionRules(appContext, updatedSources, newRules)
            AutoSkipSettings.addEnabledPackages(appContext, packagesToEnable)
            AutoSkipSettings.setLastUpdateTime(appContext, System.currentTimeMillis())
        }
        return AutoSkipUpdateResult(
            success = successful > 0,
            message = if (failed == 0) {
                appContext.getString(R.string.auto_skip_result_update_complete)
            } else {
                appContext.getString(R.string.auto_skip_result_update_complete_failed, failed)
            },
            added = added,
            changed = changed,
            failed = failed,
            removed = removed,
            details = detailLines.joinToString("\n")
        )
    }

    fun importLocalRules(json: String): AutoSkipUpdateResult {
        val imported = parseRules(json, AutoSkipRuleSource.LOCAL, "local")
            .map { it.copy(source = AutoSkipRuleSource.LOCAL, sourceId = "local") }
        if (imported.isEmpty()) return AutoSkipUpdateResult(false, appContext.getString(R.string.auto_skip_result_no_valid_rule), 0, 0, 0)
        val old = AutoSkipSettings.localRules(appContext)
        val diff = diffRules(old, imported)
        AutoSkipSettings.saveLocalRules(appContext, imported)
        AutoSkipSettings.addEnabledPackages(appContext, packageNamesForAutoEnable(imported))
        AutoSkipSettings.setLastUpdateTime(appContext, System.currentTimeMillis())
        return AutoSkipUpdateResult(
            success = true,
            message = appContext.getString(R.string.auto_skip_result_imported, imported.size),
            added = diff.added,
            changed = diff.changed,
            failed = 0,
            removed = diff.removed,
            details = "+${diff.added} ~${diff.changed} -${diff.removed}"
        )
    }

    fun saveLocalRule(json: String, replaceRuleId: String? = null): AutoSkipUpdateResult {
        val parsed = parseSingleRule(json, AutoSkipRuleSource.LOCAL, "local")
            ?.copy(source = AutoSkipRuleSource.LOCAL, sourceId = "local")
            ?: return AutoSkipUpdateResult(false, appContext.getString(R.string.auto_skip_result_no_valid_rule), 0, 0, 1)
        if (!hasExecutableMatcher(parsed)) {
            return AutoSkipUpdateResult(false, appContext.getString(R.string.auto_skip_result_rule_needs_matcher), 0, 0, 1)
        }
        val old = AutoSkipSettings.localRules(appContext)
        val existed = old.any { it.id == parsed.id || (replaceRuleId != null && it.id == replaceRuleId) }
        val next = old.filterNot { it.id == parsed.id || (replaceRuleId != null && it.id == replaceRuleId) }.toMutableList()
        next.add(parsed)
        AutoSkipSettings.saveLocalRules(appContext, next.sortedWith(compareByDescending<AutoSkipRule> { it.priority }.thenBy { it.name }))
        AutoSkipSettings.addEnabledPackages(appContext, packageNamesForAutoEnable(listOf(parsed)))
        AutoSkipSettings.setLastUpdateTime(appContext, System.currentTimeMillis())
        return AutoSkipUpdateResult(
            success = true,
            message = appContext.getString(if (existed) R.string.auto_skip_result_rule_saved else R.string.auto_skip_result_rule_added),
            added = if (existed) 0 else 1,
            changed = if (existed) 1 else 0,
            failed = 0
        )
    }

    fun deleteLocalRule(ruleId: String): AutoSkipUpdateResult {
        val old = AutoSkipSettings.localRules(appContext)
        val next = old.filterNot { it.id == ruleId }
        if (next.size == old.size) return AutoSkipUpdateResult(false, appContext.getString(R.string.auto_skip_result_rule_not_found), 0, 0, 1)
        AutoSkipSettings.saveLocalRules(appContext, next)
        AutoSkipSettings.setLastUpdateTime(appContext, System.currentTimeMillis())
        return AutoSkipUpdateResult(true, appContext.getString(R.string.auto_skip_result_rule_deleted), 0, 0, 0, removed = 1)
    }

    fun clearSubscriptionCache() {
        AutoSkipSettings.clearSubscriptionRulesCache(appContext, AutoSkipSettings.sources(appContext))
        AutoSkipSettings.setLastUpdateTime(appContext, System.currentTimeMillis())
    }

    private fun downloadRules(source: AutoSkipRuleSourceConfig): List<AutoSkipRule> {
        val connection = (URL(source.url).openConnection() as HttpURLConnection).apply {
            connectTimeout = NETWORK_TIMEOUT_MS
            readTimeout = NETWORK_TIMEOUT_MS
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json,text/plain,*/*")
        }
        return try {
            if (connection.responseCode !in 200..299) error("HTTP ${connection.responseCode}")
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            parseRules(body, AutoSkipRuleSource.SUBSCRIPTION, source.id)
        } finally {
            connection.disconnect()
        }
    }

    private fun parseRules(json: String, source: AutoSkipRuleSource, sourceId: String): List<AutoSkipRule> {
        val trimmed = normalizeRulePayload(json.trim())
        if (trimmed.isBlank()) return emptyList()
        return runCatching {
            val parsed = when {
                trimmed.startsWith("[") -> parseRuleArray(JSONArray(trimmed), source, sourceId)
                else -> parseRuleContainer(JSONObject(trimmed), source, sourceId)
            }
            parsed.filter { hasExecutableMatcher(it) }
                .distinctBy { it.id }
        }.getOrDefault(emptyList())
    }

    private fun parseSingleRule(json: String, source: AutoSkipRuleSource, sourceId: String): AutoSkipRule? {
        val trimmed = normalizeRulePayload(json.trim())
        if (trimmed.isBlank()) return null
        return runCatching {
            val obj = JSONObject(trimmed)
            AutoSkipRule.fromJson(obj, source, sourceId)
                ?: parseExternalRuleObject("*", "External", "Local", "local", obj, null, true, null, null, 0, source, sourceId)
        }.getOrNull()
    }

    private fun parseRuleContainer(obj: JSONObject, source: AutoSkipRuleSource, sourceId: String): List<AutoSkipRule> {
        obj.optJSONArray("apps")?.let { apps ->
            val result = ArrayList<AutoSkipRule>()
            result.addAll(parseExternalApps(apps, source, sourceId))
            obj.optJSONArray("globalGroups")?.let { globalGroups ->
                val globalObj = JSONObject().apply {
                    put("name", obj.optString("name", "Global"))
                    put("groups", globalGroups)
                }
                result.addAll(parseExternalApps(JSONArray().put(globalObj), source, sourceId))
            }
            return result
        }
        if (obj.has("groups") || obj.has("globalGroups") || ((obj.has("packageName") || obj.has("pkg") || obj.optString("id").contains('.')) && (obj.has("rules") || obj.has("data")))) {
            return parseExternalApps(JSONArray().put(obj), source, sourceId)
        }
        obj.optJSONObject("data")?.let { data ->
            data.optJSONArray("apps")?.let { return parseExternalApps(it, source, sourceId) }
            ruleArrayValue(data, "rules")?.let { return parseRuleArray(it, source, sourceId) }
            data.optJSONArray("groups")?.let { return parseExternalApps(JSONArray().put(data), source, sourceId) }
            ruleArrayValue(data, "data")?.let { return parseRuleArray(it, source, sourceId) }
        }
        ruleArrayValue(obj, "rules")?.let { return parseRuleArray(it, source, sourceId) }
        ruleArrayValue(obj, "data")?.let { return parseRuleArray(it, source, sourceId) }
        return listOfNotNull(AutoSkipRule.fromJson(obj, source, sourceId))
    }

    private fun parseRuleArray(array: JSONArray, source: AutoSkipRuleSource, sourceId: String): List<AutoSkipRule> {
        val rules = ArrayList<AutoSkipRule>()
        for (index in 0 until array.length()) {
            val obj = array.optJSONObject(index) ?: continue
            if (obj.has("groups") || obj.has("globalGroups") || ((obj.has("packageName") || obj.has("pkg") || obj.optString("id").contains('.')) && (obj.has("rules") || obj.has("data")))) {
                rules.addAll(parseExternalApps(JSONArray().put(obj), source, sourceId))
                continue
            }
            val direct = AutoSkipRule.fromJson(obj, source, sourceId)
            if (direct != null && hasExecutableMatcher(direct)) {
                rules.add(direct)
            } else {
                parseExternalRuleObject("*", "External", "Rules", index.toString(), obj, null, true, null, null, index, source, sourceId)?.let { rules.add(it) }
            }
        }
        return rules
    }

    private fun parseExternalApps(array: JSONArray, source: AutoSkipRuleSource, sourceId: String): List<AutoSkipRule> {
        val result = ArrayList<AutoSkipRule>()
        for (appIndex in 0 until array.length()) {
            val appObj = array.optJSONObject(appIndex) ?: continue
            val packageName = packageNameValue(appObj)
            val appName = appObj.optString("name", packageName)
            val appEnabled = externalEnabled(appObj, true)
            val appActivity = activityValue(appObj)
            appObj.optJSONArray("globalGroups")?.let { globalGroups ->
                for (groupIndex in 0 until globalGroups.length()) {
                    val group = globalGroups.optJSONObject(groupIndex) ?: continue
                    result.addAll(parseExternalGroup("*", appName.ifBlank { "Global" }, group, groupIndex, appEnabled, appActivity, source, sourceId))
                }
            }
            val groups = appObj.optJSONArray("groups")
            if (groups == null) {
                val appRules = ruleArrayValue(appObj, "rules") ?: ruleArrayValue(appObj, "data") ?: JSONArray().put(appObj)
                result.addAll(
                    parseExternalRuleArray(
                        packageName,
                        appName,
                        "Rules",
                        appIndex.toString(),
                        appRules,
                        appObj,
                        appEnabled,
                        appActivity,
                        firstInt(appObj, "priority", "order"),
                        source,
                        sourceId
                    )
                )
                continue
            }
            for (groupIndex in 0 until groups.length()) {
                val group = groups.optJSONObject(groupIndex) ?: continue
                result.addAll(parseExternalGroup(packageName, appName, group, groupIndex, appEnabled, appActivity, source, sourceId))
            }
        }
        return result
    }

    private fun parseExternalGroup(
        packageName: String,
        appName: String,
        group: JSONObject,
        groupIndex: Int,
        appEnabled: Boolean,
        appActivity: String?,
        source: AutoSkipRuleSource,
        sourceId: String
    ): List<AutoSkipRule> {
        val groupName = group.optString("name", "Group $groupIndex")
        val groupKey = group.optString("key")
            .ifBlank { group.optString("id") }
            .ifBlank { groupIndex.toString() }
        val groupRules = ruleArrayValue(group, "rules") ?: JSONArray().put(group)
        return parseExternalRuleArray(
            packageName,
            appName,
            groupName,
            groupKey,
            groupRules,
            group,
            externalEnabled(group, appEnabled),
            activityValue(group) ?: appActivity,
            firstInt(group, "priority", "order"),
            source,
            sourceId
        )
    }

    private fun parseExternalRuleArray(
        packageName: String,
        appName: String,
        groupName: String,
        groupKey: String,
        rules: JSONArray,
        groupObj: JSONObject?,
        inheritedEnabled: Boolean,
        inheritedActivity: String?,
        inheritedPriority: Int?,
        source: AutoSkipRuleSource,
        sourceId: String
    ): List<AutoSkipRule> {
        val result = ArrayList<AutoSkipRule>()
        for (ruleIndex in 0 until rules.length()) {
            val ruleObj = rules.optJSONObject(ruleIndex) ?: continue
            parseExternalRuleObject(
                packageName,
                appName,
                groupName,
                groupKey,
                ruleObj,
                groupObj,
                inheritedEnabled,
                inheritedActivity,
                inheritedPriority,
                ruleIndex,
                source,
                sourceId
            )?.let { result.add(it) }
        }
        return result
    }

    private fun parseExternalRuleObject(
        packageName: String,
        appName: String,
        groupName: String,
        groupKey: String,
        ruleObj: JSONObject,
        groupObj: JSONObject?,
        inheritedEnabled: Boolean,
        inheritedActivity: String?,
        inheritedPriority: Int?,
        ruleIndex: Int,
        source: AutoSkipRuleSource,
        sourceId: String
    ): AutoSkipRule? {
        val match = externalMatch(ruleObj, groupObj, groupName) ?: return null
        val rawRuleKey = ruleObj.optString("key")
            .ifBlank { ruleObj.optString("id") }
            .ifBlank { ruleIndex.toString() }
        val id = sanitizeRuleId("$sourceId.$packageName.$groupKey.$rawRuleKey")
        val activity = activityValue(ruleObj)
            ?: inheritedActivity
            ?: "*"
        val priority = firstInt(ruleObj, "priority", "order")
            ?: inheritedPriority
            ?: when {
                groupName.contains("开屏") || groupName.contains("广告") || groupName.contains("skip", ignoreCase = true) -> 60
                else -> 40
            }
        val delayMs = firstLong(ruleObj, "delayMs", "actionDelay", "delay")
            ?: groupObj?.let { firstLong(it, "delayMs", "actionDelay", "delay") }
            ?: 0L
        return AutoSkipRule(
            id = id,
            name = listOf(appName, groupName, ruleObj.optString("name")).filter { it.isNotBlank() }.joinToString(" / "),
            enabled = externalEnabled(ruleObj, inheritedEnabled),
            packageName = packageName.ifBlank { "*" },
            activity = activity,
            priority = priority,
            cooldownMs = (firstLong(ruleObj, "cooldownMs", "actionCd", "cd")
                ?: groupObj?.let { firstLong(it, "cooldownMs", "actionCd", "cd") }
                ?: DEFAULT_EXTERNAL_COOLDOWN_MS).coerceAtLeast(500L),
            delayMs = delayMs.coerceIn(0L, 5000L),
            match = match,
            action = externalAction(ruleObj, match.region),
            source = source,
            sourceId = sourceId
        )
    }

    private fun externalMatch(ruleObj: JSONObject, groupObj: JSONObject?, groupName: String): AutoSkipMatch? {
        val ruleSelectorValues = selectorValues(ruleObj) + quickFindSelectors(ruleObj)
        val groupSelectorValues = groupObj?.let { selectorValues(it) + quickFindSelectors(it) }.orEmpty()
        val gkdSelectors = cleanSelectors(ruleSelectorValues.ifEmpty { groupSelectorValues })
        val ruleSelectorText = selectorText(ruleObj)
        val groupSelectorText = groupObj?.let { selectorText(it) }.orEmpty()
        val combinedSelectorText = listOf(groupSelectorText, ruleSelectorText).filter { it.isNotBlank() }.joinToString("\n")
        val text = cleanPatterns(
            directPatternValues(ruleObj, "text", "texts", "quickFind") +
                extractSelectorValues(combinedSelectorText, TEXT_SELECTOR_REGEX) +
                safeKeywords(combinedSelectorText)
        ).ifEmpty {
            groupObj?.let {
                cleanPatterns(
                    directPatternValues(it, "text", "texts") +
                        extractSelectorValues(groupSelectorText, TEXT_SELECTOR_REGEX) +
                        safeKeywords(groupSelectorText)
                )
            }.orEmpty()
        }
        val desc = cleanPatterns(
            directPatternValues(ruleObj, "desc", "descs", "description", "contentDescription") +
                extractSelectorValues(combinedSelectorText, DESC_SELECTOR_REGEX)
        ).ifEmpty {
            groupObj?.let {
                cleanPatterns(directPatternValues(it, "desc", "descs", "description", "contentDescription") + extractSelectorValues(groupSelectorText, DESC_SELECTOR_REGEX))
            }.orEmpty()
        }
        val resourceId = cleanPatterns(
            directPatternValues(ruleObj, "resourceId", "resourceIds", "viewId", "vid") +
                extractSelectorValues(combinedSelectorText, ID_SELECTOR_REGEX)
        ).ifEmpty {
            groupObj?.let {
                cleanPatterns(directPatternValues(it, "resourceId", "resourceIds", "viewId", "vid") + extractSelectorValues(groupSelectorText, ID_SELECTOR_REGEX))
            }.orEmpty()
        }
        val className = cleanPatterns(
            directPatternValues(ruleObj, "className", "classNames", "nodeClass") +
                extractSelectorValues(combinedSelectorText, CLASS_SELECTOR_REGEX)
        )
        if (text.isEmpty() && desc.isEmpty() && resourceId.isEmpty() && className.isEmpty() && gkdSelectors.isEmpty()) return null
        val excludeText = cleanPatterns(excludePatternValues(ruleObj, groupObj, TEXT_SELECTOR_REGEX, "excludeText", "excludeTexts"))
        val excludeDesc = cleanPatterns(excludePatternValues(ruleObj, groupObj, DESC_SELECTOR_REGEX, "excludeDesc", "excludeDescription"))
        val excludeResourceId = cleanPatterns(excludePatternValues(ruleObj, groupObj, ID_SELECTOR_REGEX, "excludeResourceId", "excludeResourceIds"))
        val excludeGkdSelectors = cleanSelectors(listOf(ruleObj, groupObj).filterNotNull().flatMap { obj -> stringValues(obj, "excludeMatches") + stringValues(obj, "excludeSelector") })
        val region = externalRegion(ruleObj, groupObj, combinedSelectorText, groupName)
        return AutoSkipMatch(
            text = text,
            desc = desc,
            resourceId = resourceId,
            className = className,
            excludeText = excludeText,
            excludeDesc = excludeDesc,
            excludeResourceId = excludeResourceId,
            gkdSelectors = gkdSelectors,
            excludeGkdSelectors = excludeGkdSelectors,
            visible = true,
            region = region
        )
    }

    private fun directPatternValues(obj: JSONObject, vararg keys: String): List<String> {
        return keys.flatMap { stringValues(obj, it) }
    }

    private fun packageNameValue(obj: JSONObject): String {
        val explicit = obj.optString("packageName")
            .ifBlank { obj.optString("pkg") }
        if (explicit.isNotBlank()) return explicit
        val id = obj.optString("id")
        return if (id.contains('.')) id else "*"
    }

    private fun ruleArrayValue(obj: JSONObject, key: String): JSONArray? {
        val value = obj.opt(key) ?: return null
        return when (value) {
            is JSONArray -> value
            is JSONObject -> JSONArray().put(value)
            else -> null
        }
    }

    private fun selectorValues(obj: JSONObject): List<String> {
        return listOf("matches", "selector", "match")
            .flatMap { key -> stringValues(obj, key) }
            .map { it.trim() }
            .filter { looksLikeGkdSelector(it) }
    }

    private fun quickFindSelectors(obj: JSONObject): List<String> {
        return stringValues(obj, "quickFind")
            .map { it.trim() }
            .filter { it.isNotBlank() && it.length <= MAX_PATTERN_LENGTH }
            .map { value ->
                val escaped = selectorLiteral(value)
                if (value.contains(':') || value.contains('/')) {
                    "([id*=\"$escaped\"]) || ([vid=\"$escaped\"])"
                } else {
                    "([text*=\"$escaped\"]) || ([desc*=\"$escaped\"]) || ([id*=\"$escaped\"]) || ([vid=\"$escaped\"])"
                }
            }
    }

    private fun selectorLiteral(value: String): String {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
    }

    private fun selectorText(obj: JSONObject): String {
        return listOf(
            "matches",
            "quickFind",
            "selector",
            "match",
            "action",
            "preKeys"
        ).flatMap { stringValues(obj, it) }.joinToString("\n")
    }

    private fun normalizeRulePayload(raw: String): String {
        if (raw.isBlank()) return raw
        val withoutComments = stripJson5Comments(raw.removePrefix("\uFEFF"))
        if (!looksLikeJson5(withoutComments)) return withoutComments.trim()
        val normalizedStrings = normalizeJson5Strings(withoutComments)
        val quotedKeys = quoteJson5Keys(normalizedStrings)
        return removeTrailingCommas(quotedKeys).trim()
    }

    private fun looksLikeJson5(value: String): Boolean {
        val trimmed = value.trimStart()
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) return true
        return Regex("""(?m)^\s*[_A-Za-z$][_A-Za-z0-9$]*\s*:""").containsMatchIn(value)
    }

    private fun stripJson5Comments(value: String): String {
        val out = StringBuilder(value.length)
        var quote: Char? = null
        var index = 0
        while (index < value.length) {
            val c = value[index]
            if (quote != null) {
                out.append(c)
                if (c == '\\' && index + 1 < value.length) {
                    index += 1
                    out.append(value[index])
                } else if (c == quote) {
                    quote = null
                }
            } else {
                when {
                    c == '\'' || c == '"' || c == '`' -> {
                        quote = c
                        out.append(c)
                    }
                    c == '/' && value.getOrNull(index + 1) == '/' -> {
                        index += 2
                        while (index < value.length && value[index] != '\n' && value[index] != '\r') index += 1
                        if (index < value.length) out.append(value[index])
                    }
                    c == '/' && value.getOrNull(index + 1) == '*' -> {
                        index += 2
                        while (index + 1 < value.length && !(value[index] == '*' && value[index + 1] == '/')) index += 1
                        index += 1
                    }
                    else -> out.append(c)
                }
            }
            index += 1
        }
        return out.toString()
    }

    private fun normalizeJson5Strings(value: String): String {
        val out = StringBuilder(value.length)
        var quote: Char? = null
        var index = 0
        while (index < value.length) {
            val c = value[index]
            if (quote == null) {
                if (c == '\'' || c == '`') {
                    quote = c
                    out.append('"')
                } else {
                    if (c == '"') quote = c
                    out.append(c)
                }
            } else if (quote == '"') {
                out.append(c)
                if (c == '\\' && index + 1 < value.length) {
                    index += 1
                    out.append(value[index])
                } else if (c == '"') {
                    quote = null
                }
            } else {
                when (c) {
                    '\\' -> {
                        if (index + 1 < value.length) {
                            index += 1
                            val escaped = value[index]
                            when (escaped) {
                                quote -> out.append(escaped)
                                '"' -> out.append("\\\"")
                                else -> out.append('\\').append(escaped)
                            }
                        } else {
                            out.append('\\')
                        }
                    }
                    '"' -> out.append("\\\"")
                    quote -> {
                        out.append('"')
                        quote = null
                    }
                    else -> out.append(c)
                }
            }
            index += 1
        }
        return out.toString()
    }

    private fun quoteJson5Keys(value: String): String {
        val out = StringBuilder(value.length)
        var quote: Char? = null
        var index = 0
        var expectingKey = false
        while (index < value.length) {
            val c = value[index]
            if (quote != null) {
                out.append(c)
                if (c == '\\' && index + 1 < value.length) {
                    index += 1
                    out.append(value[index])
                } else if (c == quote) {
                    quote = null
                }
            } else {
                when {
                    c == '"' -> {
                        quote = c
                        out.append(c)
                    }
                    c == '{' || c == ',' -> {
                        expectingKey = true
                        out.append(c)
                    }
                    c == ':' -> {
                        expectingKey = false
                        out.append(c)
                    }
                    expectingKey && c.isWhitespace() -> out.append(c)
                    expectingKey && isJson5IdentifierStart(c) -> {
                        val start = index
                        index += 1
                        while (index < value.length && isJson5IdentifierPart(value[index])) index += 1
                        val key = value.substring(start, index)
                        var cursor = index
                        while (cursor < value.length && value[cursor].isWhitespace()) cursor += 1
                        if (value.getOrNull(cursor) == ':') {
                            out.append('"').append(key).append('"')
                        } else {
                            out.append(key)
                        }
                        index -= 1
                        expectingKey = false
                    }
                    else -> out.append(c)
                }
            }
            index += 1
        }
        return out.toString()
    }

    private fun removeTrailingCommas(value: String): String {
        val out = StringBuilder(value.length)
        var quote: Char? = null
        var index = 0
        while (index < value.length) {
            val c = value[index]
            if (quote != null) {
                out.append(c)
                if (c == '\\' && index + 1 < value.length) {
                    index += 1
                    out.append(value[index])
                } else if (c == quote) {
                    quote = null
                }
            } else if (c == '"') {
                quote = c
                out.append(c)
            } else if (c == ',') {
                var cursor = index + 1
                while (cursor < value.length && value[cursor].isWhitespace()) cursor += 1
                if (value.getOrNull(cursor) != '}' && value.getOrNull(cursor) != ']') out.append(c)
            } else {
                out.append(c)
            }
            index += 1
        }
        return out.toString()
    }

    private fun isJson5IdentifierStart(c: Char): Boolean {
        return c == '_' || c == '$' || c.isLetter()
    }

    private fun isJson5IdentifierPart(c: Char): Boolean {
        return isJson5IdentifierStart(c) || c.isDigit()
    }

    private fun looksLikeGkdSelector(value: String): Boolean {
        val text = value.trim()
        if (text.length > MAX_SELECTOR_LENGTH || text.startsWith("{") || text.startsWith("[{") || text.startsWith("[\"")) return false
        return text.contains('[') ||
            text.contains(" > ") ||
            text.contains(" < ") ||
            text.contains(" << ") ||
            text.contains(" + ") ||
            text.contains(" - ") ||
            text.startsWith("@") ||
            GKD_CLASS_SELECTOR_REGEX.matches(text)
    }

    private fun stringValues(obj: JSONObject, key: String): List<String> {
        val value = obj.opt(key) ?: return emptyList()
        return when (value) {
            is JSONArray -> {
                val result = ArrayList<String>()
                for (index in 0 until value.length()) {
                    val item = value.opt(index)
                    when (item) {
                        is String -> result.add(item)
                        is Number -> result.add(item.toString())
                        is Boolean -> result.add(item.toString())
                        is JSONObject -> result.add(item.toString())
                    }
                }
                result
            }
            is JSONObject -> listOf(value.toString())
            is String -> listOf(value)
            is Number -> listOf(value.toString())
            is Boolean -> listOf(value.toString())
            else -> emptyList()
        }
    }

    private fun firstString(obj: JSONObject, vararg keys: String): String? {
        keys.forEach { key ->
            stringValues(obj, key).firstOrNull { it.isNotBlank() }?.let { return it }
        }
        return null
    }

    private fun activityValue(obj: JSONObject): String? {
        return listOf("activityIds", "activityId", "activity", "activityName")
            .flatMap { key -> stringValues(obj, key) }
            .map { it.trim() }
            .filter { it.isNotEmpty() && it != "[]" }
            .distinct()
            .joinToString(",")
            .takeIf { it.isNotBlank() }
    }

    private fun firstInt(obj: JSONObject, vararg keys: String): Int? {
        keys.forEach { key ->
            if (!obj.has(key)) return@forEach
            val value = obj.opt(key)
            when (value) {
                is Number -> return value.toInt()
                is String -> value.trim().toIntOrNull()?.let { return it }
            }
        }
        return null
    }

    private fun firstLong(obj: JSONObject, vararg keys: String): Long? {
        keys.forEach { key ->
            if (!obj.has(key)) return@forEach
            val value = obj.opt(key)
            when (value) {
                is Number -> return value.toLong()
                is String -> value.trim().toLongOrNull()?.let { return it }
            }
        }
        return null
    }

    private fun externalEnabled(obj: JSONObject, fallback: Boolean): Boolean {
        return when {
            obj.has("enabled") -> obj.optBoolean("enabled", fallback)
            obj.has("enable") -> obj.optBoolean("enable", fallback)
            obj.has("disabled") -> !obj.optBoolean("disabled", !fallback)
            else -> fallback
        }
    }

    private fun externalAction(ruleObj: JSONObject, region: AutoSkipRegion?): AutoSkipAction {
        val text = listOf("action", "position", "tap", "click", "actionMaximum").flatMap { stringValues(ruleObj, it) }.joinToString("\n")
        val strategy = when {
            text.contains("top_right", ignoreCase = true) || region?.name.equals("top_right", ignoreCase = true) -> AutoSkipTapStrategy.TOP_RIGHT
            text.contains("bottom_right", ignoreCase = true) || region?.name.equals("bottom_right", ignoreCase = true) -> AutoSkipTapStrategy.BOTTOM_RIGHT
            text.contains("center", ignoreCase = true) -> AutoSkipTapStrategy.CENTER
            else -> AutoSkipTapStrategy.PROBE
        }
        return AutoSkipAction(tapStrategy = strategy)
    }

    private fun excludePatternValues(ruleObj: JSONObject, groupObj: JSONObject?, regex: Regex, vararg directKeys: String): List<String> {
        val direct = directKeys.flatMap { key -> directPatternValues(ruleObj, key) }
        val groupDirect = groupObj?.let { group -> directKeys.flatMap { key -> directPatternValues(group, key) } }.orEmpty()
        val selectorValues = listOf(ruleObj, groupObj).filterNotNull()
            .flatMap { obj -> stringValues(obj, "excludeMatches") + stringValues(obj, "excludeSelector") }
            .flatMap { selector -> extractSelectorValues(selector, regex) }
        return direct + groupDirect + selectorValues
    }

    private fun externalRegion(ruleObj: JSONObject, groupObj: JSONObject?, selectorText: String, groupName: String): AutoSkipRegion? {
        val explicit = firstString(ruleObj, "region", "bounds") ?: groupObj?.let { firstString(it, "region", "bounds") }
        val value = explicit.orEmpty()
        return when {
            value.contains("top", ignoreCase = true) && value.contains("right", ignoreCase = true) -> AutoSkipRegion("top_right")
            value.contains("bottom", ignoreCase = true) && value.contains("right", ignoreCase = true) -> AutoSkipRegion("bottom_right")
            value.equals("top", ignoreCase = true) || value.contains("top", ignoreCase = true) -> AutoSkipRegion("top")
            value.equals("bottom", ignoreCase = true) || value.contains("bottom", ignoreCase = true) -> AutoSkipRegion("bottom")
            selectorText.contains("top_right", ignoreCase = true) || selectorText.contains("top-right", ignoreCase = true) || groupName.contains("开屏") -> AutoSkipRegion("top_right")
            selectorText.contains("bottom_right", ignoreCase = true) || selectorText.contains("bottom-right", ignoreCase = true) -> AutoSkipRegion("bottom_right")
            else -> null
        }
    }

    private fun extractSelectorValues(selector: String, regex: Regex): List<String> {
        if (selector.isBlank()) return emptyList()
        return regex.findAll(selector).map { it.groupValues[1] }.toList()
    }

    private fun safeKeywords(selector: String): List<String> {
        if (selector.isBlank()) return emptyList()
        return SAFE_SELECTOR_KEYWORDS.filter { selector.contains(it, ignoreCase = true) }
    }

    private fun cleanSelectors(values: List<String>): List<String> {
        return values.map { it.trim() }
            .filter { it.isNotBlank() && it.length <= MAX_SELECTOR_LENGTH }
            .filter { looksLikeGkdSelector(it) }
            .distinctBy { it.lowercase(Locale.ROOT) }
    }

    private fun cleanPatterns(values: List<String>): List<String> {
        return values.map { it.trim().trim('"', '\'', '`') }
            .filter { it.isNotBlank() && it.length <= MAX_PATTERN_LENGTH }
            .distinctBy { it.lowercase(Locale.ROOT) }
    }

    private fun sanitizeRuleId(value: String): String {
        return value.replace(Regex("[^A-Za-z0-9_.-]"), ".")
            .replace(Regex("\\.+"), ".")
            .trim('.')
            .take(160)
            .ifBlank { "rule.${System.currentTimeMillis()}" }
    }

    private fun hasExecutableMatcher(rule: AutoSkipRule): Boolean {
        return rule.match.text.isNotEmpty() ||
            rule.match.desc.isNotEmpty() ||
            rule.match.resourceId.isNotEmpty() ||
            rule.match.className.isNotEmpty() ||
            rule.match.gkdSelectors.isNotEmpty()
    }

    private fun diffRules(previous: List<AutoSkipRule>, current: List<AutoSkipRule>): RuleDiff {
        val previousById = previous.associateBy { it.id }
        val currentById = current.associateBy { it.id }
        val previousIds = previousById.keys
        val currentIds = currentById.keys
        val added = currentIds.minus(previousIds).size
        val removed = previousIds.minus(currentIds).size
        val changed = currentIds.intersect(previousIds).count { id ->
            previousById[id]?.toJson()?.toString() != currentById[id]?.toJson()?.toString()
        }
        return RuleDiff(added, changed, removed)
    }

    private fun packagesForAutoEnable(previous: List<AutoSkipRule>, current: List<AutoSkipRule>): Set<String> {
        val previousById = previous.associateBy { it.id }
        return packageNamesForAutoEnable(current.filter { rule ->
            val old = previousById[rule.id]
            old == null || old.packageName != rule.packageName
        })
    }

    private fun packageNamesForAutoEnable(rules: Iterable<AutoSkipRule>): Set<String> {
        return rules.map { it.packageName.trim() }
            .filter { it.isNotEmpty() && it != "*" && !isSensitivePackage(it) }
            .toSet()
    }

    private fun compactUrl(url: String): String {
        return url.removePrefix("https://").removePrefix("http://").take(80)
    }

    private fun isSensitivePackage(packageName: String): Boolean {
        if (packageName == appContext.packageName) return true
        return SENSITIVE_PACKAGE_KEYWORDS.any { packageName.contains(it, ignoreCase = true) }
    }

    private fun isSensitiveActivity(activity: String?): Boolean {
        val value = activity.orEmpty()
        return SENSITIVE_ACTIVITY_KEYWORDS.any { value.contains(it, ignoreCase = true) }
    }

    private fun builtinRules(): List<AutoSkipRule> {
        val genericSkipRegex = """^\\s*(?:\\d+\\s*(?:s|S|秒)?\\s*)?(?:跳[过過](?:广告|廣告)?|[Ss]kip(?:\\s*[Aa]d(?:s)?)?)(?:\\s*[：:/\\-]?\\s*\\d+\\s*(?:s|S|秒)?)?\\s*$"""
        return listOf(
            AutoSkipRule(
                id = "builtin.skip.top_right",
                name = context.getString(com.hujiayucc.hook.R.string.auto_skip_builtin_top_right_skip),
                enabled = true,
                packageName = "*",
                activity = "*",
                priority = 80,
                cooldownMs = 3000L,
                delayMs = 250L,
                match = AutoSkipMatch(
                    gkdSelectors = listOf(
                        """*[text~="$genericSkipRegex"]""",
                        """*[desc~="$genericSkipRegex"]"""
                    ),
                    visible = true
                ),
                action = AutoSkipAction(tapStrategy = AutoSkipTapStrategy.PROBE),
                source = AutoSkipRuleSource.BUILTIN
            ),
            AutoSkipRule(
                id = "builtin.later.generic",
                name = context.getString(com.hujiayucc.hook.R.string.auto_skip_builtin_later_button),
                enabled = true,
                packageName = "*",
                activity = "*",
                priority = 30,
                cooldownMs = 5000L,
                delayMs = 200L,
                match = AutoSkipMatch(
                    text = listOf("以后再说", "暂不开启", "暂不", "Not now"),
                    desc = listOf("以后再说", "暂不开启", "Not now"),
                    visible = true
                ),
                action = AutoSkipAction(tapStrategy = AutoSkipTapStrategy.CENTER),
                source = AutoSkipRuleSource.BUILTIN
            ),
            AutoSkipRule(
                id = "builtin.close.top_right",
                name = context.getString(com.hujiayucc.hook.R.string.auto_skip_builtin_top_right_close),
                enabled = true,
                packageName = "*",
                activity = "*",
                priority = 20,
                cooldownMs = 5000L,
                delayMs = 300L,
                match = AutoSkipMatch(
                    text = listOf("关闭", "Close"),
                    desc = listOf("关闭", "Close"),
                    visible = true,
                    region = AutoSkipRegion("top_right")
                ),
                action = AutoSkipAction(tapStrategy = AutoSkipTapStrategy.CENTER),
                source = AutoSkipRuleSource.BUILTIN
            )
        )
    }

    companion object {
        private const val NETWORK_TIMEOUT_MS = 5000
        private const val MAX_PATTERN_LENGTH = 64
        private const val DEFAULT_EXTERNAL_COOLDOWN_MS = 3000L
        private const val MAX_SELECTOR_LENGTH = 512
        private val GKD_CLASS_SELECTOR_REGEX = Regex("""@?\*|@?[A-Za-z_][A-Za-z0-9_$.]*""")
        private val TEXT_SELECTOR_REGEX = Regex("""(?:text|textMatches|textStartsWith|textEndsWith)\s*(?:==|=|\*=|\^=|~=)?\s*[\"']([^\"']{1,64})[\"']""", RegexOption.IGNORE_CASE)
        private val DESC_SELECTOR_REGEX = Regex("""(?:desc|description|contentDescription)\s*(?:==|=|\*=|\^=|~=)?\s*[\"']([^\"']{1,64})[\"']""", RegexOption.IGNORE_CASE)
        private val ID_SELECTOR_REGEX = Regex("""(?:id|vid|resourceId)\s*(?:==|=|\*=|\^=|~=)?\s*[\"']([^\"']{1,96})[\"']""", RegexOption.IGNORE_CASE)
        private val CLASS_SELECTOR_REGEX = Regex("""(?:className|class|nodeClass)\s*(?:==|=|\*=|\^=|~=)?\s*[\"']([^\"']{1,96})[\"']""", RegexOption.IGNORE_CASE)
        private val SAFE_SELECTOR_KEYWORDS = listOf(
            "跳过",
            "跳过广告",
            "关闭",
            "以后再说",
            "暂不开启",
            "暂不",
            "Skip",
            "Close",
            "Not now"
        )
        private val SENSITIVE_PACKAGE_KEYWORDS = listOf(
            "bank",
            "pay",
            "wallet",
            "alipay",
            "tencent.mm",
            "installer",
            "packageinstaller",
            "permissioncontroller",
            "shizuku",
            "lspatch",
            "lsposed"
        )
        private val SENSITIVE_ACTIVITY_KEYWORDS = listOf(
            "pay",
            "login",
            "auth",
            "permission",
            "install",
            "captcha",
            "keyboard",
            "password"
        )
    }
}

private data class RuleDiff(
    val added: Int,
    val changed: Int,
    val removed: Int
)

data class AutoSkipRuleStats(
    val total: Int,
    val enabled: Int,
    val builtin: Int,
    val subscription: Int,
    val local: Int,
    val sourceCount: Int,
    val lastUpdateTime: Long
)

data class AutoSkipUpdateResult(
    val success: Boolean,
    val message: String,
    val added: Int,
    val changed: Int,
    val failed: Int,
    val removed: Int = 0,
    val details: String = ""
)

package com.hujiayucc.hook.autoskip

import android.accessibilityservice.AccessibilityService
import android.graphics.Point
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class AutoSkipEngine(
    private val service: AccessibilityService,
    private val errorReporter: (String, Throwable) -> Unit = { _, _ -> }
) {
    private val appContext = service.applicationContext
    private val repository = AutoSkipRuleRepository(appContext)
    private val clickExecutor = AutoSkipClickExecutor(service)
    private val executor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "AutoSkipEngine").apply { isDaemon = true }
    }
    private val evaluationGate = LatestPendingGate<PendingEvaluation>()
    private val lastRuleClickAt = HashMap<String, Long>()
    private val lastAppClickAt = HashMap<String, Long>()
    @Volatile
    private var runtimeConfig = AutoSkipRuntimeConfig.disabled()
    private var lastEventKey = ""
    private var lastEventAt = 0L

    init {
        executor.scheduleWithFixedDelay(
            {
                runCatching { AutoSkipSettings.runtimeConfig(appContext) }
                    .onSuccess { runtimeConfig = it }
                    .onFailure { error -> errorReporter("config", error) }
            },
            0L,
            CONFIG_REFRESH_INTERVAL_MS,
            TimeUnit.MILLISECONDS
        )
    }

    fun onAccessibilityEvent(event: AccessibilityEvent) {
        val config = runtimeConfig
        val packageName = event.packageName?.toString().orEmpty()
        if (!config.shouldProcess(packageName)) return
        if (!isSupportedEvent(event.eventType)) return
        val activity = event.className?.toString().orEmpty()
        val now = SystemClock.uptimeMillis()
        val eventKey = "$packageName|$activity|${event.eventType}"
        if (eventKey == lastEventKey && now - lastEventAt < MIN_EVENT_INTERVAL_MS) return
        lastEventKey = eventKey
        lastEventAt = now
        val evaluation = PendingEvaluation(packageName, activity, config)
        evaluationGate.offer(evaluation)?.let(::submitEvaluation)
    }

    fun shutdown() {
        evaluationGate.clearPending()
        lastRuleClickAt.clear()
        lastAppClickAt.clear()
        AutoSkipRuleRepository.clearMemoryCache()
        executor.shutdownNow()
    }

    fun trimMemory() {
        runCatching {
            executor.execute {
                lastRuleClickAt.clear()
                lastAppClickAt.clear()
                AutoSkipRuleRepository.clearMemoryCache()
            }
        }.onFailure {
            lastRuleClickAt.clear()
            lastAppClickAt.clear()
        }
    }

    private fun submitEvaluation(evaluation: PendingEvaluation) {
        runCatching {
            executor.execute {
                evaluate(evaluation.packageName, evaluation.activity, evaluation.runtimeConfig)
            }
        }.onFailure { error ->
            finishEvaluation()
            errorReporter("schedule_evaluate", error)
        }
    }

    private fun evaluate(packageName: String, activity: String, runtimeConfig: AutoSkipRuntimeConfig) {
        var releaseEvaluation = true
        try {
            releaseEvaluation = evaluateNow(packageName, activity, runtimeConfig)
        } catch (error: Throwable) {
            errorReporter("evaluate", error)
        } finally {
            if (releaseEvaluation) finishEvaluation()
        }
    }

    private fun finishEvaluation() {
        evaluationGate.complete()?.let(::submitEvaluation)
    }

    private fun evaluateNow(
        packageName: String,
        activity: String,
        runtimeConfig: AutoSkipRuntimeConfig
    ): Boolean {
        val root = service.rootInActiveWindow ?: return true
        val activePackageName = activePackageName(root, packageName)
        if (!runtimeConfig.shouldProcess(activePackageName)) return true
        val rules = repository.executableRules(activePackageName, activity, runtimeConfig.ruleDataGeneration)
        if (rules.isEmpty()) return true
        val metrics = appContext.resources.displayMetrics
        val matcher = AutoSkipRuleMatcher(metrics.widthPixels, metrics.heightPixels)
        val match = matcher.findMatch(root, rules) ?: return true
        if (!canClick(activePackageName, match.rule)) return true
        if (match.rule.delayMs > 0L) {
            return scheduleDelayedClick(
                activePackageName,
                activity,
                matcher,
                match.rule,
                runtimeConfig,
                match.rule.delayMs
            )
        }
        clickMatched(activePackageName, activity, matcher, match, runtimeConfig)
        return true
    }

    private fun scheduleDelayedClick(
        activePackageName: String,
        activity: String,
        matcher: AutoSkipRuleMatcher,
        rule: AutoSkipRule,
        runtimeConfig: AutoSkipRuntimeConfig,
        delayMs: Long
    ): Boolean {
        return runCatching {
            executor.schedule(
                {
                    try {
                        clickIfStillMatched(activePackageName, activity, matcher, rule, runtimeConfig)
                    } catch (error: Throwable) {
                        errorReporter("delayed_click", error)
                    } finally {
                        finishEvaluation()
                    }
                },
                delayMs,
                TimeUnit.MILLISECONDS
            )
            false
        }.getOrDefault(true)
    }

    private fun clickIfStillMatched(
        activePackageName: String,
        activity: String,
        matcher: AutoSkipRuleMatcher,
        rule: AutoSkipRule,
        scheduledConfig: AutoSkipRuntimeConfig
    ) {
        val latestConfig = refreshRuntimeConfig("delayed_click_config") ?: return
        val refreshedRoot = service.rootInActiveWindow ?: return
        val refreshedPackageName = activePackageName(refreshedRoot, activePackageName)
        if (refreshedPackageName != activePackageName) return
        val currentRules = repository.executableRules(
            refreshedPackageName,
            activity,
            latestConfig.ruleDataGeneration
        )
        if (!isRuleExecutionCurrent(
                expectedGeneration = scheduledConfig.ruleDataGeneration,
                currentConfig = latestConfig,
                packageName = refreshedPackageName,
                rule = rule,
                executableRules = currentRules
            )
        ) return
        val refreshedMatch = matcher.findMatch(refreshedRoot, listOf(rule)) ?: return
        if (!canClick(activePackageName, rule)) return
        clickMatched(activePackageName, activity, matcher, refreshedMatch, latestConfig)
    }

    private fun clickMatched(
        activePackageName: String,
        activity: String,
        matcher: AutoSkipRuleMatcher,
        match: AutoSkipMatchResult,
        runtimeConfig: AutoSkipRuntimeConfig
    ) {
        val expectedWindowId = match.node.windowId
        val result = clickExecutor.execute(
            match.rule,
            match.node,
            match.points,
            runtimeConfig,
            verifier = {
                verifyClickResult(matcher, match.rule, activePackageName, runtimeConfig)
            },
            asynchronousVerifier = AutoSkipAsyncVerifier { clickResult, verifier, retry ->
                scheduleVerifyResult(
                    VerificationContext(activePackageName, activity, match.rule, clickResult),
                    verifier,
                    retry
                )
            },
            attemptValidator = { point ->
                isClickAttemptStillValid(
                    activePackageName = activePackageName,
                    activity = activity,
                    expectedWindowId = expectedWindowId,
                    matcher = matcher,
                    rule = match.rule,
                    expectedGeneration = runtimeConfig.ruleDataGeneration,
                    point = point
                )
            }
        )
        if (result.success) {
            markClickCooldown(activePackageName, match.rule)
            return
        }
        recordClickResult(activePackageName, activity, match.rule, result)
    }

    private fun isClickAttemptStillValid(
        activePackageName: String,
        activity: String,
        expectedWindowId: Int,
        matcher: AutoSkipRuleMatcher,
        rule: AutoSkipRule,
        expectedGeneration: Long,
        point: Point
    ): Boolean {
        val latestConfig = refreshRuntimeConfig("click_attempt_config") ?: return false
        return runCatching {
            val root = service.rootInActiveWindow ?: return@runCatching false
            val currentPackageName = activePackageName(root, activePackageName)
            if (currentPackageName != activePackageName || root.windowId != expectedWindowId) {
                return@runCatching false
            }
            val currentRules = repository.executableRules(
                currentPackageName,
                activity,
                latestConfig.ruleDataGeneration
            )
            if (!isRuleExecutionCurrent(
                    expectedGeneration = expectedGeneration,
                    currentConfig = latestConfig,
                    packageName = currentPackageName,
                    rule = rule,
                    executableRules = currentRules
                )
            ) return@runCatching false
            val currentMatch = matcher.findMatch(root, listOf(rule)) ?: return@runCatching false
            currentMatch.points.any { currentPoint -> currentPoint == point }
        }.onFailure { error ->
            errorReporter("click_attempt_guard", error)
        }.getOrDefault(false)
    }

    private fun refreshRuntimeConfig(stage: String): AutoSkipRuntimeConfig? {
        return runCatching { AutoSkipSettings.runtimeConfig(appContext) }
            .onSuccess { latestConfig -> runtimeConfig = latestConfig }
            .onFailure { error -> errorReporter(stage, error) }
            .getOrNull()
    }

    private fun scheduleVerifyResult(
        context: VerificationContext,
        verifier: () -> AutoSkipClickVerification,
        retry: (() -> AutoSkipExecutionResult?)?
    ) {
        runCatching {
            executor.schedule(
                {
                    runCatching { verifyScheduledResult(context, verifier, retry) }
                        .onFailure { error -> errorReporter("verify", error) }
                },
                VERIFY_DELAY_MS,
                TimeUnit.MILLISECONDS
            )
        }.onFailure { error ->
            recordClickResult(
                context.activePackageName,
                context.activity,
                context.rule,
                context.result.copy(message = "ok; verification schedule failed: ${error.javaClass.simpleName}")
            )
        }
    }

    private fun verifyScheduledResult(
        context: VerificationContext,
        verifier: () -> AutoSkipClickVerification,
        retry: (() -> AutoSkipExecutionResult?)?
    ) {
        val outcome = runAutoSkipVerificationFlow(context.result, verifier, retry)
        if (outcome.markCooldown) {
            markClickCooldown(context.activePackageName, context.rule)
        }
        outcome.resultToRecord?.let { result ->
            recordClickResult(context.activePackageName, context.activity, context.rule, result)
        }
    }

    private fun markClickCooldown(packageName: String, rule: AutoSkipRule) {
        val now = SystemClock.uptimeMillis()
        pruneClickCooldowns(now)
        lastAppClickAt[packageName] = now
        lastRuleClickAt[rule.id] = now
        boundCooldownEntries(lastRuleClickAt, MAX_RULE_COOLDOWN_ENTRIES)
    }

    private fun recordClickResult(
        activePackageName: String,
        activity: String,
        rule: AutoSkipRule,
        result: AutoSkipExecutionResult
    ) {
        AutoSkipSettings.recordHit(
            appContext,
            AutoSkipHitLog(
                time = System.currentTimeMillis(),
                packageName = activePackageName,
                activity = activity,
                ruleId = rule.id,
                ruleName = rule.name,
                executor = result.executor,
                x = result.point?.x ?: 0,
                y = result.point?.y ?: 0,
                result = result.message
            )
        )
    }

    private fun verifyClickResult(
        matcher: AutoSkipRuleMatcher,
        rule: AutoSkipRule,
        expectedPackageName: String,
        runtimeConfig: AutoSkipRuntimeConfig
    ): AutoSkipClickVerification {
        val root = service.rootInActiveWindow ?: return AutoSkipClickVerification(true, "ok; verified: window changed")
        val currentPackageName = activePackageName(root, expectedPackageName)
        if (currentPackageName != expectedPackageName || !runtimeConfig.shouldProcess(currentPackageName)) {
            return AutoSkipClickVerification(true, "ok; verified: window changed")
        }
        val stillMatched = matcher.findMatch(root, listOf(rule)) != null
        return if (stillMatched) {
            AutoSkipClickVerification(false, "ok; verified: target still visible")
        } else {
            AutoSkipClickVerification(true, "ok; verified: target disappeared")
        }
    }

    private fun canClick(packageName: String, rule: AutoSkipRule): Boolean {
        val now = SystemClock.uptimeMillis()
        pruneClickCooldowns(now)
        val lastApp = lastAppClickAt[packageName] ?: 0L
        if (now - lastApp < APP_COOLDOWN_MS) return false
        val lastRule = lastRuleClickAt[rule.id]
        if (lastRule != null) {
            if (now - lastRule < rule.cooldownMs) return false
            lastRuleClickAt.remove(rule.id)
        }
        return true
    }

    private fun pruneClickCooldowns(now: Long) {
        removeExpiredCooldownEntries(lastAppClickAt, now, APP_COOLDOWN_MS)
        boundCooldownEntries(lastRuleClickAt, MAX_RULE_COOLDOWN_ENTRIES)
    }

    private fun activePackageName(root: AccessibilityNodeInfo, fallback: String): String {
        return root.packageName?.toString()?.takeIf { it.isNotBlank() } ?: fallback
    }

    private fun isSupportedEvent(eventType: Int): Boolean {
        return eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
            eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED ||
            eventType == AccessibilityEvent.TYPE_VIEW_FOCUSED
    }

    private data class VerificationContext(
        val activePackageName: String,
        val activity: String,
        val rule: AutoSkipRule,
        val result: AutoSkipExecutionResult
    )

    private data class PendingEvaluation(
        val packageName: String,
        val activity: String,
        val runtimeConfig: AutoSkipRuntimeConfig
    )

    companion object {
        private const val CONFIG_REFRESH_INTERVAL_MS = 1_000L
        private const val MIN_EVENT_INTERVAL_MS = 250L
        private const val APP_COOLDOWN_MS = 3000L
        private const val VERIFY_DELAY_MS = 350L
        private const val MAX_RULE_COOLDOWN_ENTRIES = 1024
    }
}

internal fun removeExpiredCooldownEntries(
    entries: MutableMap<String, Long>,
    now: Long,
    durationMs: Long
) {
    entries.entries.removeAll { (_, timestamp) ->
        now < timestamp || now - timestamp >= durationMs
    }
}

internal fun boundCooldownEntries(entries: MutableMap<String, Long>, maxEntries: Int) {
    val retainedEntries = maxEntries.coerceAtLeast(0)
    while (entries.size > retainedEntries) {
        val oldestKey = entries.minByOrNull { (_, timestamp) -> timestamp }?.key ?: return
        entries.remove(oldestKey)
    }
}

internal fun isRuleExecutionCurrent(
    expectedGeneration: Long,
    currentConfig: AutoSkipRuntimeConfig,
    packageName: String,
    rule: AutoSkipRule,
    executableRules: List<AutoSkipRule>
): Boolean {
    if (currentConfig.ruleDataGeneration != expectedGeneration) return false
    if (!currentConfig.shouldProcess(packageName)) return false
    return executableRules.any { currentRule -> currentRule == rule }
}

internal data class AutoSkipVerificationOutcome(
    val resultToRecord: AutoSkipExecutionResult?,
    val markCooldown: Boolean
)

internal fun runAutoSkipVerificationFlow(
    originalResult: AutoSkipExecutionResult,
    verifier: () -> AutoSkipClickVerification,
    retry: (() -> AutoSkipExecutionResult?)?
): AutoSkipVerificationOutcome {
    val verification = runCatching { verifier() }.getOrElse { error ->
        AutoSkipClickVerification(true, "ok; verification failed: ${error.javaClass.simpleName}")
    }
    if (verification.accepted) {
        return AutoSkipVerificationOutcome(
            resultToRecord = originalResult.copy(message = verification.message),
            markCooldown = false
        )
    }
    val retryResult = retry?.invoke()
        ?: return AutoSkipVerificationOutcome(
            resultToRecord = originalResult.copy(message = verification.message),
            markCooldown = false
        )
    val retrySucceeded = retryResult.success
    return AutoSkipVerificationOutcome(
        resultToRecord = retryResult.takeUnless {
            retrySucceeded && it.message == VERIFICATION_SCHEDULED_MESSAGE
        },
        markCooldown = retrySucceeded
    )
}
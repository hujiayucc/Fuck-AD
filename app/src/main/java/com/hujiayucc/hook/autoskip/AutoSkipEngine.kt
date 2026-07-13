package com.hujiayucc.hook.autoskip

import android.accessibilityservice.AccessibilityService
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
        executor.shutdownNow()
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
        runtimeConfig: AutoSkipRuntimeConfig
    ) {
        val refreshedRoot = service.rootInActiveWindow ?: return
        val refreshedPackageName = activePackageName(refreshedRoot, activePackageName)
        if (refreshedPackageName != activePackageName || !runtimeConfig.shouldProcess(refreshedPackageName)) return
        val refreshedMatch = matcher.findMatch(refreshedRoot, listOf(rule)) ?: return
        clickMatched(activePackageName, activity, matcher, refreshedMatch, runtimeConfig)
    }

    private fun clickMatched(
        activePackageName: String,
        activity: String,
        matcher: AutoSkipRuleMatcher,
        match: AutoSkipMatchResult,
        runtimeConfig: AutoSkipRuntimeConfig
    ) {
        val result = clickExecutor.execute(
            match.rule,
            match.node,
            match.points,
            runtimeConfig,
            verifier = {
                verifyClickResult(matcher, match.rule, activePackageName, runtimeConfig)
            },
            asynchronousVerifier = AutoSkipAsyncVerifier { clickResult, verifier, retry ->
                scheduleVerifyResult(activePackageName, activity, match.rule, clickResult, verifier, retry)
            }
        )
        if (result.success) {
            markClickCooldown(activePackageName, match.rule)
            return
        }
        recordClickResult(activePackageName, activity, match.rule, result)
    }

    private fun scheduleVerifyResult(
        activePackageName: String,
        activity: String,
        rule: AutoSkipRule,
        result: AutoSkipExecutionResult,
        verifier: () -> AutoSkipClickVerification,
        retry: (() -> AutoSkipExecutionResult?)?
    ) {
        runCatching {
            executor.schedule(
                {
                    runCatching {
                        val verification = runCatching { verifier() }.getOrElse { error ->
                            AutoSkipClickVerification(true, "ok; verification failed: ${error.javaClass.simpleName}")
                        }
                        if (verification.accepted) {
                            recordClickResult(activePackageName, activity, rule, result.copy(message = verification.message))
                            return@runCatching
                        }

                        val retryResult = retry?.invoke()
                        when {
                            retryResult == null -> {
                                recordClickResult(activePackageName, activity, rule, result.copy(message = verification.message))
                            }

                            retryResult.success -> {
                                markClickCooldown(activePackageName, rule)
                                if (retryResult.message != VERIFICATION_SCHEDULED_MESSAGE) {
                                    recordClickResult(activePackageName, activity, rule, retryResult)
                                }
                            }

                            else -> {
                                recordClickResult(activePackageName, activity, rule, retryResult)
                            }
                        }
                    }.onFailure { error ->
                        errorReporter("verify", error)
                    }
                },
                VERIFY_DELAY_MS,
                TimeUnit.MILLISECONDS
            )
        }.onFailure { error ->
            recordClickResult(
                activePackageName,
                activity,
                rule,
                result.copy(message = "ok; verification schedule failed: ${error.javaClass.simpleName}")
            )
        }
    }

    private fun markClickCooldown(packageName: String, rule: AutoSkipRule) {
        val now = SystemClock.uptimeMillis()
        lastAppClickAt[packageName] = now
        lastRuleClickAt[rule.id] = now
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
        val lastApp = lastAppClickAt[packageName] ?: 0L
        if (now - lastApp < APP_COOLDOWN_MS) return false
        val lastRule = lastRuleClickAt[rule.id] ?: 0L
        if (now - lastRule < rule.cooldownMs) return false
        return true
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
    }
}
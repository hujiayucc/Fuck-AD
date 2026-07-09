package com.hujiayucc.hook.autoskip

import android.accessibilityservice.AccessibilityService
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class AutoSkipEngine(private val service: AccessibilityService) {
    private val appContext = service.applicationContext
    private val repository = AutoSkipRuleRepository(appContext)
    private val clickExecutor = AutoSkipClickExecutor(service)
    private val executor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "AutoSkipEngine").apply { isDaemon = true }
    }
    private val evaluating = AtomicBoolean(false)
    private val lastRuleClickAt = HashMap<String, Long>()
    private val lastAppClickAt = HashMap<String, Long>()
    private var lastEventAt = 0L

    fun onAccessibilityEvent(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString().orEmpty()
        if (!shouldProcessPackage(packageName)) return
        if (!isSupportedEvent(event.eventType)) return
        val now = SystemClock.uptimeMillis()
        if (now - lastEventAt < MIN_EVENT_INTERVAL_MS) return
        lastEventAt = now
        val activity = event.className?.toString().orEmpty()
        if (!evaluating.compareAndSet(false, true)) return
        executor.execute {
            evaluate(packageName, activity)
        }
    }

    fun shutdown() {
        executor.shutdownNow()
    }

    private fun evaluate(packageName: String, activity: String) {
        var releaseEvaluation = true
        try {
            releaseEvaluation = evaluateNow(packageName, activity)
        } finally {
            if (releaseEvaluation) evaluating.set(false)
        }
    }

    private fun evaluateNow(packageName: String, activity: String): Boolean {
        val root = service.rootInActiveWindow ?: return true
        val activePackageName = activePackageName(root, packageName)
        if (!shouldProcessPackage(activePackageName)) return true
        val rules = repository.executableRules(activePackageName, activity)
        if (rules.isEmpty()) return true
        val metrics = appContext.resources.displayMetrics
        val matcher = AutoSkipRuleMatcher(metrics.widthPixels, metrics.heightPixels)
        val match = matcher.findMatch(root, rules) ?: return true
        if (!canClick(activePackageName, match.rule)) return true
        if (match.rule.delayMs > 0L) {
            return scheduleDelayedClick(activePackageName, activity, matcher, match.rule, match.rule.delayMs)
        }
        clickMatched(activePackageName, activity, matcher, match)
        return true
    }

    private fun scheduleDelayedClick(
        activePackageName: String,
        activity: String,
        matcher: AutoSkipRuleMatcher,
        rule: AutoSkipRule,
        delayMs: Long
    ): Boolean {
        return runCatching {
            executor.schedule(
                {
                    try {
                        clickIfStillMatched(activePackageName, activity, matcher, rule)
                    } finally {
                        evaluating.set(false)
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
        rule: AutoSkipRule
    ) {
        val refreshedRoot = service.rootInActiveWindow ?: return
        val refreshedPackageName = activePackageName(refreshedRoot, activePackageName)
        if (refreshedPackageName != activePackageName || !shouldProcessPackage(refreshedPackageName)) return
        val refreshedMatch = matcher.findMatch(refreshedRoot, listOf(rule)) ?: return
        clickMatched(activePackageName, activity, matcher, refreshedMatch)
    }

    private fun clickMatched(
        activePackageName: String,
        activity: String,
        matcher: AutoSkipRuleMatcher,
        match: AutoSkipMatchResult
    ) {
        val result = clickExecutor.execute(
            match.rule,
            match.node,
            match.points
        ) {
            verifyClickResult(matcher, match.rule, activePackageName)
        }
        val now = SystemClock.uptimeMillis()
        if (result.success) {
            lastAppClickAt[activePackageName] = now
            lastRuleClickAt[match.rule.id] = now
        }
        AutoSkipSettings.recordHit(
            appContext,
            AutoSkipHitLog(
                time = System.currentTimeMillis(),
                packageName = activePackageName,
                activity = activity,
                ruleId = match.rule.id,
                ruleName = match.rule.name,
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
        expectedPackageName: String
    ): AutoSkipClickVerification {
        Thread.sleep(VERIFY_DELAY_MS)
        val root = service.rootInActiveWindow ?: return AutoSkipClickVerification(true, "ok; verified: window changed")
        val currentPackageName = activePackageName(root, expectedPackageName)
        if (currentPackageName != expectedPackageName || !shouldProcessPackage(currentPackageName)) {
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

    private fun shouldProcessPackage(packageName: String): Boolean {
        return packageName.isNotBlank() &&
            AutoSkipSettings.isEnabled(appContext) &&
            AutoSkipSettings.isAppEnabled(appContext, packageName)
    }

    private fun activePackageName(root: AccessibilityNodeInfo, fallback: String): String {
        return root.packageName?.toString()?.takeIf { it.isNotBlank() } ?: fallback
    }

    private fun isSupportedEvent(eventType: Int): Boolean {
        return eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
            eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED
    }

    companion object {
        private const val MIN_EVENT_INTERVAL_MS = 250L
        private const val APP_COOLDOWN_MS = 3000L
        private const val VERIFY_DELAY_MS = 350L
    }
}
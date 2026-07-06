package com.hujiayucc.hook.autoskip

import android.accessibilityservice.AccessibilityService
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class AutoSkipEngine(private val service: AccessibilityService) {
    private val appContext = service.applicationContext
    private val repository = AutoSkipRuleRepository(appContext)
    private val clickExecutor = AutoSkipClickExecutor(service)
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
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
            try {
                evaluate(packageName, activity)
            } finally {
                evaluating.set(false)
            }
        }
    }

    fun shutdown() {
        executor.shutdownNow()
    }

    private fun evaluate(packageName: String, activity: String) {
        val root = service.rootInActiveWindow ?: return
        val activePackageName = activePackageName(root, packageName)
        if (!shouldProcessPackage(activePackageName)) return
        val rules = repository.executableRules(activePackageName, activity)
        if (rules.isEmpty()) return
        val metrics = appContext.resources.displayMetrics
        val matcher = AutoSkipRuleMatcher(metrics.widthPixels, metrics.heightPixels)
        val match = matcher.findMatch(root, rules) ?: return
        if (!canClick(activePackageName, match.rule)) return
        if (match.rule.delayMs > 0L) Thread.sleep(match.rule.delayMs)
        val refreshedRoot = service.rootInActiveWindow ?: return
        val refreshedPackageName = activePackageName(refreshedRoot, activePackageName)
        if (refreshedPackageName != activePackageName || !shouldProcessPackage(refreshedPackageName)) return
        val refreshedMatch = matcher.findMatch(refreshedRoot, listOf(match.rule)) ?: return
        val result = clickExecutor.execute(
            refreshedMatch.rule,
            refreshedMatch.node,
            refreshedMatch.points
        ) {
            verifyClickResult(matcher, refreshedMatch.rule, refreshedPackageName)
        }
        val now = SystemClock.uptimeMillis()
        if (result.success) {
            lastAppClickAt[activePackageName] = now
            lastRuleClickAt[refreshedMatch.rule.id] = now
        }
        AutoSkipSettings.recordHit(
            appContext,
            AutoSkipHitLog(
                time = System.currentTimeMillis(),
                packageName = activePackageName,
                activity = activity,
                ruleId = refreshedMatch.rule.id,
                ruleName = refreshedMatch.rule.name,
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
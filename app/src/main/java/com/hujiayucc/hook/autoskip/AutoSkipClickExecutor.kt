package com.hujiayucc.hook.autoskip

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.pm.PackageManager
import android.graphics.Path
import android.graphics.Point
import android.view.accessibility.AccessibilityNodeInfo
import com.hujiayucc.hook.utils.ShizukuProcessExecutor
import com.hujiayucc.hook.utils.waitForSuccess
import rikka.shizuku.Shizuku
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class AutoSkipClickExecutor(private val service: AccessibilityService) {

    fun execute(
        rule: AutoSkipRule,
        node: AccessibilityNodeInfo,
        points: List<Point>,
        runtimeConfig: AutoSkipRuntimeConfig,
        verifier: (() -> AutoSkipClickVerification)? = null,
        asynchronousVerifier: AutoSkipAsyncVerifier? = null
    ): AutoSkipExecutionResult {
        if (points.isEmpty()) return AutoSkipExecutionResult(false, "none", null, "No tap point")
        val context = AttemptContext(
            node = node,
            attempts = clickAttempts(rule, points, runtimeConfig),
            firstPoint = points.firstOrNull(),
            runtimeConfig = runtimeConfig,
            verifier = verifier,
            asynchronousVerifier = asynchronousVerifier
        )
        return executeAttempts(context, startIndex = 0)
    }

    private fun executeAttempts(context: AttemptContext, startIndex: Int): AutoSkipExecutionResult {
        val deferVerification = context.asynchronousVerifier != null && context.verifier != null
        val outcome = runAutoSkipAttemptFlow(
            attempts = context.attempts,
            startIndex = startIndex,
            isEnabled = { attempt -> isExecutorEnabled(attempt.type, context.runtimeConfig) },
            execute = { attempt -> runAttempt(attempt.type, context.node, attempt.point) },
            verifier = context.verifier,
            deferVerification = deferVerification
        )
        outcome.acceptedAttempt?.let { attempt ->
            val acceptedResult = AutoSkipExecutionResult(true, attempt.type.name, attempt.point, "ok")
            if (deferVerification) {
                val scheduledResult = acceptedResult.copy(message = VERIFICATION_SCHEDULED_MESSAGE)
                context.asynchronousVerifier!!.verify(
                    scheduledResult,
                    context.verifier!!,
                    retry = outcome.retryStartIndex?.let { retryIndex ->
                        { executeAttempts(context, startIndex = retryIndex) }
                    }
                )
                return scheduledResult
            }
            return acceptedResult.copy(message = outcome.verification?.message ?: "ok")
        }
        val rejectedAttempt = outcome.lastRejectedAttempt
        val rejection = outcome.lastRejection
        if (rejectedAttempt != null && rejection != null) {
            return AutoSkipExecutionResult(true, rejectedAttempt.type.name, rejectedAttempt.point, rejection.message)
        }
        return AutoSkipExecutionResult(false, "none", context.firstPoint, "All executors failed")
    }

    private fun clickAttempts(
        rule: AutoSkipRule,
        points: List<Point>,
        runtimeConfig: AutoSkipRuntimeConfig
    ): List<ClickAttempt> {
        return prioritizedExecutors(rule.action.fallbackExecutors, runtimeConfig).flatMap { type ->
            pointsForExecutor(type, points).map { point -> ClickAttempt(type, point) }
        }
    }

    private fun runAttempt(
        type: AutoSkipClickExecutorType,
        node: AccessibilityNodeInfo,
        point: Point
    ): Boolean {
        return when (type) {
            AutoSkipClickExecutorType.ACCESSIBILITY_GESTURE -> dispatchGesture(point)
            AutoSkipClickExecutorType.SHIZUKU_INPUT -> runShizukuTap(point)
            AutoSkipClickExecutorType.ROOT_INPUT -> runRootTap(point)
            AutoSkipClickExecutorType.ACCESSIBILITY_ACTION -> runAccessibilityAction(node)
        }
    }

    private fun pointsForExecutor(type: AutoSkipClickExecutorType, points: List<Point>): List<Point> {
        return if (type == AutoSkipClickExecutorType.ACCESSIBILITY_ACTION) {
            points.take(1)
        } else {
            points.take(MAX_POINTS_PER_RULE)
        }
    }

    private fun prioritizedExecutors(
        executors: List<AutoSkipClickExecutorType>,
        runtimeConfig: AutoSkipRuntimeConfig
    ): List<AutoSkipClickExecutorType> {
        val enabledPrivileged = listOf(
            AutoSkipClickExecutorType.SHIZUKU_INPUT,
            AutoSkipClickExecutorType.ROOT_INPUT
        ).filter { type -> type in executors && isExecutorEnabled(type, runtimeConfig) }
        if (enabledPrivileged.isEmpty()) return executors
        return enabledPrivileged + executors.filterNot { type -> type in enabledPrivileged }
    }

    private fun isExecutorEnabled(type: AutoSkipClickExecutorType, runtimeConfig: AutoSkipRuntimeConfig): Boolean {
        return when (type) {
            AutoSkipClickExecutorType.ACCESSIBILITY_GESTURE -> true
            AutoSkipClickExecutorType.SHIZUKU_INPUT -> runtimeConfig.useShizukuInput && hasShizukuPermission()
            AutoSkipClickExecutorType.ROOT_INPUT -> runtimeConfig.useRootInput
            AutoSkipClickExecutorType.ACCESSIBILITY_ACTION -> true
        }
    }

    private fun dispatchGesture(point: Point): Boolean {
        val path = Path().apply { moveTo(point.x.toFloat(), point.y.toFloat()) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, TAP_DURATION_MS))
            .build()
        val latch = CountDownLatch(1)
        var completed = false
        val accepted = service.dispatchGesture(
            gesture,
            object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    completed = true
                    latch.countDown()
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    completed = false
                    latch.countDown()
                }
            },
            null
        )
        if (!accepted) return false
        latch.await(GESTURE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        return completed
    }

    private fun runAccessibilityAction(node: AccessibilityNodeInfo): Boolean {
        var candidate: AccessibilityNodeInfo? = node
        var depth = 0
        while (candidate != null && depth < 4) {
            if (candidate.isClickable && candidate.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            candidate = candidate.parent
            depth += 1
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    private fun runShizukuTap(point: Point): Boolean {
        return ShizukuProcessExecutor.run(inputTapCommand(point), COMMAND_TIMEOUT_SECONDS)
    }

    private fun runRootTap(point: Point): Boolean {
        return runCatching {
            Runtime.getRuntime().exec(arrayOf("su", "-c", "input tap ${point.x} ${point.y}"))
                .waitForSuccess(COMMAND_TIMEOUT_SECONDS)
        }.getOrDefault(false)
    }

    private fun inputTapCommand(point: Point): Array<String> {
        return arrayOf("input", "tap", point.x.toString(), point.y.toString())
    }

    private fun hasShizukuPermission(): Boolean {
        return runCatching {
            Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
    }

    companion object {
        private const val TAP_DURATION_MS = 80L
        private const val GESTURE_TIMEOUT_MS = 600L
        private const val COMMAND_TIMEOUT_SECONDS = 2L
        private const val MAX_POINTS_PER_RULE = 4
    }
}

internal data class AutoSkipAttemptOutcome<T>(
    val acceptedAttempt: T? = null,
    val verification: AutoSkipClickVerification? = null,
    val retryStartIndex: Int? = null,
    val lastRejectedAttempt: T? = null,
    val lastRejection: AutoSkipClickVerification? = null
)

internal fun <T> runAutoSkipAttemptFlow(
    attempts: List<T>,
    startIndex: Int,
    isEnabled: (T) -> Boolean,
    execute: (T) -> Boolean,
    verifier: (() -> AutoSkipClickVerification)?,
    deferVerification: Boolean
): AutoSkipAttemptOutcome<T> {
    var lastRejectedAttempt: T? = null
    var lastRejection: AutoSkipClickVerification? = null
    for (index in startIndex until attempts.size) {
        val attempt = attempts[index]
        if (!isEnabled(attempt) || !execute(attempt)) continue
        if (deferVerification) {
            return AutoSkipAttemptOutcome(
                acceptedAttempt = attempt,
                retryStartIndex = (index + 1).takeIf { it < attempts.size }
            )
        }
        val verification = verifier?.invoke()
        if (verification == null || verification.accepted) {
            return AutoSkipAttemptOutcome(acceptedAttempt = attempt, verification = verification)
        }
        lastRejectedAttempt = attempt
        lastRejection = verification
    }
    return AutoSkipAttemptOutcome(
        lastRejectedAttempt = lastRejectedAttempt,
        lastRejection = lastRejection
    )
}

internal const val VERIFICATION_SCHEDULED_MESSAGE = "ok; verification scheduled"

data class AutoSkipClickVerification(
    val accepted: Boolean,
    val message: String
)

fun interface AutoSkipAsyncVerifier {
    fun verify(
        result: AutoSkipExecutionResult,
        verifier: () -> AutoSkipClickVerification,
        retry: (() -> AutoSkipExecutionResult?)?
    )
}

private data class AttemptContext(
    val node: AccessibilityNodeInfo,
    val attempts: List<ClickAttempt>,
    val firstPoint: Point?,
    val runtimeConfig: AutoSkipRuntimeConfig,
    val verifier: (() -> AutoSkipClickVerification)?,
    val asynchronousVerifier: AutoSkipAsyncVerifier?
)

private data class ClickAttempt(
    val type: AutoSkipClickExecutorType,
    val point: Point
)

data class AutoSkipExecutionResult(
    val success: Boolean,
    val executor: String,
    val point: Point?,
    val message: String
)
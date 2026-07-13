package com.hujiayucc.hook.autoskip

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.pm.PackageManager
import android.graphics.Path
import android.graphics.Point
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import rikka.shizuku.Shizuku
import java.lang.reflect.Method
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
        val attempts = clickAttempts(rule, points, runtimeConfig)
        return executeAttempts(
            node = node,
            attempts = attempts,
            startIndex = 0,
            firstPoint = points.firstOrNull(),
            runtimeConfig = runtimeConfig,
            verifier = verifier,
            asynchronousVerifier = asynchronousVerifier
        )
    }

    private fun executeAttempts(
        node: AccessibilityNodeInfo,
        attempts: List<ClickAttempt>,
        startIndex: Int,
        firstPoint: Point?,
        runtimeConfig: AutoSkipRuntimeConfig,
        verifier: (() -> AutoSkipClickVerification)?,
        asynchronousVerifier: AutoSkipAsyncVerifier?
    ): AutoSkipExecutionResult {
        var lastRejectedResult: AutoSkipExecutionResult? = null
        for (index in startIndex until attempts.size) {
            val attempt = attempts[index]
            if (!isExecutorEnabled(attempt.type, runtimeConfig)) continue
            if (!runAttempt(attempt.type, node, attempt.point)) continue

            val acceptedResult = AutoSkipExecutionResult(true, attempt.type.name, attempt.point, "ok")
            if (asynchronousVerifier != null && verifier != null) {
                val scheduledResult = acceptedResult.copy(message = "ok; verification scheduled")
                asynchronousVerifier.verify(
                    scheduledResult,
                    verifier,
                    retry = if (index + 1 < attempts.size) {
                        {
                            executeAttempts(
                                node = node,
                                attempts = attempts,
                                startIndex = index + 1,
                                firstPoint = firstPoint,
                                runtimeConfig = runtimeConfig,
                                verifier = verifier,
                                asynchronousVerifier = asynchronousVerifier
                            )
                        }
                    } else {
                        null
                    }
                )
                return scheduledResult
            }

            val verification = verifier?.invoke()
            if (verification == null || verification.accepted) {
                return acceptedResult.copy(message = verification?.message ?: "ok")
            }
            lastRejectedResult = acceptedResult.copy(message = verification.message)
        }
        return lastRejectedResult ?: AutoSkipExecutionResult(false, "none", firstPoint, "All executors failed")
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
        return runCatching {
            shizukuNewProcessMethod()
                .invoke(null, inputTapCommand(point), null, null)
                .let { it as Process }
                .waitForSuccess()
        }.getOrDefault(false)
    }

    private fun runRootTap(point: Point): Boolean {
        return runCatching {
            Runtime.getRuntime().exec(arrayOf("su", "-c", "input tap ${point.x} ${point.y}")).waitForSuccess()
        }.getOrDefault(false)
    }

    private fun inputTapCommand(point: Point): Array<String> {
        return arrayOf("input", "tap", point.x.toString(), point.y.toString())
    }

    private fun hasShizukuPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false
        return runCatching {
            Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
    }

    private fun shizukuNewProcessMethod(): Method {
        cachedShizukuNewProcessMethod?.let { return it }
        return synchronized(AutoSkipClickExecutor::class.java) {
            cachedShizukuNewProcessMethod ?: Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            ).apply { isAccessible = true }
                .also { cachedShizukuNewProcessMethod = it }
        }
    }

    private fun Process.waitForSuccess(): Boolean {
        val finished = waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        if (!finished) {
            destroy()
            return false
        }
        return exitValue() == 0
    }

    companion object {
        @Volatile
        private var cachedShizukuNewProcessMethod: Method? = null

        private const val TAP_DURATION_MS = 80L
        private const val GESTURE_TIMEOUT_MS = 600L
        private const val COMMAND_TIMEOUT_SECONDS = 2L
        private const val MAX_POINTS_PER_RULE = 4
    }
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
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
    private val appContext = service.applicationContext

    fun execute(
        rule: AutoSkipRule,
        node: AccessibilityNodeInfo,
        points: List<Point>,
        verifier: (() -> AutoSkipClickVerification)? = null
    ): AutoSkipExecutionResult {
        if (points.isEmpty()) return AutoSkipExecutionResult(false, "none", null, "No tap point")
        var lastAcceptedResult: AutoSkipExecutionResult? = null
        prioritizedExecutors(rule.action.fallbackExecutors).forEach { type ->
            if (!isExecutorEnabled(type)) return@forEach
            pointsForExecutor(type, points).forEach { point ->
                val success = when (type) {
                    AutoSkipClickExecutorType.ACCESSIBILITY_GESTURE -> dispatchGesture(point)
                    AutoSkipClickExecutorType.SHIZUKU_INPUT -> runShizukuTap(point)
                    AutoSkipClickExecutorType.ROOT_INPUT -> runRootTap(point)
                    AutoSkipClickExecutorType.ACCESSIBILITY_ACTION -> runAccessibilityAction(node)
                }
                if (success) {
                    val verification = verifier?.invoke()
                    if (verification == null || verification.accepted) {
                        return AutoSkipExecutionResult(true, type.name, point, verification?.message ?: "ok")
                    }
                    lastAcceptedResult = AutoSkipExecutionResult(true, type.name, point, verification.message)
                }
            }
        }
        return lastAcceptedResult ?: AutoSkipExecutionResult(false, "none", points.firstOrNull(), "All executors failed")
    }

    private fun pointsForExecutor(type: AutoSkipClickExecutorType, points: List<Point>): List<Point> {
        return if (type == AutoSkipClickExecutorType.ACCESSIBILITY_ACTION) {
            points.take(1)
        } else {
            points.take(MAX_POINTS_PER_RULE)
        }
    }

    private fun prioritizedExecutors(executors: List<AutoSkipClickExecutorType>): List<AutoSkipClickExecutorType> {
        val enabledPrivileged = listOf(
            AutoSkipClickExecutorType.SHIZUKU_INPUT,
            AutoSkipClickExecutorType.ROOT_INPUT
        ).filter { type -> type in executors && isExecutorEnabled(type) }
        if (enabledPrivileged.isEmpty()) return executors
        return enabledPrivileged + executors.filterNot { type -> type in enabledPrivileged }
    }

    private fun isExecutorEnabled(type: AutoSkipClickExecutorType): Boolean {
        return when (type) {
            AutoSkipClickExecutorType.ACCESSIBILITY_GESTURE -> true
            AutoSkipClickExecutorType.SHIZUKU_INPUT -> AutoSkipSettings.useShizukuInput(appContext) && hasShizukuPermission()
            AutoSkipClickExecutorType.ROOT_INPUT -> AutoSkipSettings.useRootInput(appContext)
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
        return Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java
        ).apply { isAccessible = true }
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
        private const val TAP_DURATION_MS = 80L
        private const val GESTURE_TIMEOUT_MS = 600L
        private const val COMMAND_TIMEOUT_SECONDS = 2L
        private const val MAX_POINTS_PER_RULE = 4
    }
}

data class AutoSkipClickVerification(
    val accepted: Boolean,
    val message: String
)

data class AutoSkipExecutionResult(
    val success: Boolean,
    val executor: String,
    val point: Point?,
    val message: String
)
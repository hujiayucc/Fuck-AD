package com.hujiayucc.hook.autoskip

import android.graphics.Point
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

class AutoSkipRuleMatcher(
    private val screenWidth: Int,
    private val screenHeight: Int
) {
    private val legacyGkdMatcher = GkdSelectorMatcher(screenWidth, screenHeight)
    private val gkdMatcher = GkdOfficialSelectorMatcher(screenWidth, screenHeight, legacyGkdMatcher)

    fun findMatch(root: AccessibilityNodeInfo, rules: List<AutoSkipRule>): AutoSkipMatchResult? {
        val selectorSnapshot = gkdMatcher.snapshot(root)
        var nodeSnapshot: AutoSkipNodeSnapshot? = null
        rules.forEach { rule ->
            findGkdMatchingNode(selectorSnapshot, rule)?.let { selectorMatch ->
                if (selectorMatch.node != null) {
                    val points = tapPoints(selectorMatch.node, rule)
                    if (points.isNotEmpty()) return AutoSkipMatchResult(rule, selectorMatch.node, points)
                }
                if (selectorMatch.attempted) return@forEach
            }
            val snapshot = nodeSnapshot ?: AutoSkipNodeSnapshot(root).also { nodeSnapshot = it }
            findMatchingNode(snapshot, rule)?.let { ref ->
                val points = tapPoints(ref, rule)
                if (points.isNotEmpty()) return AutoSkipMatchResult(rule, ref.node, points)
            }
        }
        return null
    }

    private fun findGkdMatchingNode(snapshot: GkdSelectorSnapshot, rule: AutoSkipRule): GkdSelectorMatch? {
        if (rule.match.gkdSelectors.isEmpty() && rule.match.excludeGkdSelectors.isEmpty()) return null
        if (gkdMatcher.hasAny(snapshot, rule.match.excludeGkdSelectors)) return GkdSelectorMatch(null, attempted = true)
        return gkdMatcher.findFirst(snapshot, rule.match.gkdSelectors, rule.match.visible, rule.match.region)
    }

    private fun findMatchingNode(snapshot: AutoSkipNodeSnapshot, rule: AutoSkipRule): AutoSkipNodeRef? {
        return snapshot.nodes.firstOrNull { ref -> matches(ref, rule) }
    }

    private fun matches(ref: AutoSkipNodeRef, rule: AutoSkipRule): Boolean {
        val bounds = ref.bounds
        if (rule.match.visible && !ref.visibleToUser) return false
        if (!isReasonableBounds(bounds)) return false
        if (rule.match.region?.contains(bounds, screenWidth, screenHeight) == false) return false
        if (matchesExcludedField(ref, rule.match)) return false
        return matchesPositiveField(ref, rule.match)
    }

    private fun matchesExcludedField(ref: AutoSkipNodeRef, match: AutoSkipMatch): Boolean {
        if (match.excludeText.isNotEmpty() && matchesAny(ref.text, match.excludeText)) return true
        if (match.excludeDesc.isNotEmpty() && matchesAny(ref.desc, match.excludeDesc)) return true
        if (match.excludeResourceId.isNotEmpty() && matchesAny(ref.resourceId, match.excludeResourceId)) return true
        return false
    }

    private fun matchesPositiveField(ref: AutoSkipNodeRef, match: AutoSkipMatch): Boolean {
        if (match.text.isNotEmpty() && matchesAny(ref.text, match.text)) return true
        if (match.desc.isNotEmpty() && matchesAny(ref.desc, match.desc)) return true
        if (match.resourceId.isNotEmpty() && matchesAny(ref.resourceId, match.resourceId)) return true
        if (match.className.isNotEmpty() && matchesAny(ref.className, match.className)) return true
        return false
    }

    private fun matchesAny(value: String?, patterns: List<String>): Boolean {
        if (patterns.isEmpty()) return true
        val target = value?.trim().orEmpty()
        if (target.isEmpty()) return false
        return patterns.any { pattern ->
            val normalized = pattern.trim()
            target.equals(normalized, ignoreCase = true) || target.contains(normalized, ignoreCase = true)
        }
    }

    private fun tapPoints(ref: AutoSkipNodeRef, rule: AutoSkipRule): List<Point> {
        val bounds = clickableBounds(ref)
        if (!isReasonableBounds(bounds)) return emptyList()
        return when (rule.action.tapStrategy) {
            AutoSkipTapStrategy.CENTER -> listOf(bounds.pointAt(0.5f, 0.5f))
            AutoSkipTapStrategy.TOP_RIGHT -> listOf(bounds.pointAt(0.86f, 0.28f))
            AutoSkipTapStrategy.BOTTOM_RIGHT -> listOf(bounds.pointAt(0.86f, 0.72f))
            AutoSkipTapStrategy.CUSTOM_RATIO -> listOf(bounds.pointAt(rule.action.customXRatio, rule.action.customYRatio))
            AutoSkipTapStrategy.PROBE -> probePoints(ref)
        }.filter { point -> point.x in 0 until screenWidth && point.y in 0 until screenHeight }.distinctBy { it.x to it.y }
    }

    private fun tapPoints(node: AccessibilityNodeInfo, rule: AutoSkipRule): List<Point> {
        val bounds = clickableBounds(node)
        if (!isReasonableBounds(bounds)) return emptyList()
        return when (rule.action.tapStrategy) {
            AutoSkipTapStrategy.CENTER -> listOf(bounds.pointAt(0.5f, 0.5f))
            AutoSkipTapStrategy.TOP_RIGHT -> listOf(bounds.pointAt(0.86f, 0.28f))
            AutoSkipTapStrategy.BOTTOM_RIGHT -> listOf(bounds.pointAt(0.86f, 0.72f))
            AutoSkipTapStrategy.CUSTOM_RATIO -> listOf(bounds.pointAt(rule.action.customXRatio, rule.action.customYRatio))
            AutoSkipTapStrategy.PROBE -> probePoints(node)
        }.filter { point -> point.x in 0 until screenWidth && point.y in 0 until screenHeight }.distinctBy { it.x to it.y }
    }

    private fun probePoints(ref: AutoSkipNodeRef): List<Point> {
        val points = ArrayList<Point>()
        val own = ref.bounds
        if (isReasonableBounds(own)) {
            points.add(own.pointAt(0.5f, 0.5f))
            points.add(own.pointAt(0.86f, 0.32f))
            points.add(own.pointAt(0.86f, 0.68f))
        }
        var parent = ref.parent
        var depth = 0
        while (parent != null && depth < 3) {
            val bounds = parent.bounds
            if (isReasonableBounds(bounds)) {
                points.add(bounds.pointAt(0.5f, 0.5f))
                points.add(bounds.pointAt(0.86f, 0.32f))
            }
            if (parent.clickable) break
            parent = parent.parent
            depth += 1
        }
        return points
    }

    private fun probePoints(node: AccessibilityNodeInfo): List<Point> {
        val points = ArrayList<Point>()
        val own = node.bounds()
        if (isReasonableBounds(own)) {
            points.add(own.pointAt(0.5f, 0.5f))
            points.add(own.pointAt(0.86f, 0.32f))
            points.add(own.pointAt(0.86f, 0.68f))
        }
        var parent = node.parent
        var depth = 0
        while (parent != null && depth < 3) {
            val bounds = parent.bounds()
            if (isReasonableBounds(bounds)) {
                points.add(bounds.pointAt(0.5f, 0.5f))
                points.add(bounds.pointAt(0.86f, 0.32f))
            }
            if (parent.isClickable) break
            parent = parent.parent
            depth += 1
        }
        return points
    }

    private fun clickableBounds(ref: AutoSkipNodeRef): Rect {
        var candidate: AutoSkipNodeRef? = ref
        var depth = 0
        while (candidate != null && depth < 3) {
            val bounds = candidate.bounds
            if (candidate.clickable && isReasonableBounds(bounds)) return bounds
            candidate = candidate.parent
            depth += 1
        }
        return ref.bounds
    }

    private fun clickableBounds(node: AccessibilityNodeInfo): Rect {
        var candidate: AccessibilityNodeInfo? = node
        var depth = 0
        while (candidate != null && depth < 3) {
            val bounds = candidate.bounds()
            if (candidate.isClickable && isReasonableBounds(bounds)) return bounds
            candidate = candidate.parent
            depth += 1
        }
        return node.bounds()
    }

    private fun isReasonableBounds(bounds: Rect): Boolean {
        if (bounds.isEmpty) return false
        if (bounds.right <= 0 || bounds.bottom <= 0 || bounds.left >= screenWidth || bounds.top >= screenHeight) return false
        val width = bounds.width()
        val height = bounds.height()
        val screenArea = screenWidth * screenHeight
        val area = width * height
        return width >= 8 && height >= 8 && area <= screenArea * 0.6f
    }

    private fun AccessibilityNodeInfo.bounds(): Rect {
        val rect = Rect()
        getBoundsInScreen(rect)
        return rect
    }

    private fun Rect.pointAt(xRatio: Float, yRatio: Float): Point {
        return Point(
            (left + width() * xRatio.coerceIn(0f, 1f)).toInt(),
            (top + height() * yRatio.coerceIn(0f, 1f)).toInt()
        )
    }

    private inner class AutoSkipNodeSnapshot(root: AccessibilityNodeInfo) {
        val nodes = ArrayList<AutoSkipNodeRef>()

        init {
            append(root, parent = null)
        }

        private fun append(node: AccessibilityNodeInfo, parent: AutoSkipNodeRef?): AutoSkipNodeRef {
            val ref = AutoSkipNodeRef(
                node = node,
                parent = parent,
                bounds = node.bounds(),
                text = node.text?.toString(),
                desc = node.contentDescription?.toString(),
                resourceId = node.viewIdResourceName,
                className = node.className?.toString(),
                visibleToUser = node.isVisibleToUser,
                clickable = node.isClickable
            )
            nodes.add(ref)
            for (childIndex in 0 until node.childCount) {
                val child = node.getChild(childIndex) ?: continue
                append(child, ref)
            }
            return ref
        }
    }

    private class AutoSkipNodeRef(
        val node: AccessibilityNodeInfo,
        val parent: AutoSkipNodeRef?,
        val bounds: Rect,
        val text: String?,
        val desc: String?,
        val resourceId: String?,
        val className: String?,
        val visibleToUser: Boolean,
        val clickable: Boolean
    )
}

data class AutoSkipMatchResult(
    val rule: AutoSkipRule,
    val node: AccessibilityNodeInfo,
    val points: List<Point>
)

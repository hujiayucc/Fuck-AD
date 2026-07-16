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
    private val tapPointResolver = AutoSkipTapPointResolver(screenWidth, screenHeight)

    fun findMatch(root: AccessibilityNodeInfo, rules: List<AutoSkipRule>): AutoSkipMatchResult? {
        val selectorSnapshot = gkdMatcher.snapshot(root)
        var nodeSnapshot: AutoSkipNodeSnapshot? = null
        rules.forEach { rule ->
            findGkdMatchingNode(selectorSnapshot, rule)?.let { selectorMatch ->
                if (selectorMatch.node != null) {
                    val points = tapPointResolver.resolve(AccessibilityTapNode(selectorMatch.node), rule.action.toTapRequest())
                        .map { point -> Point(point.x, point.y) }
                    if (points.isNotEmpty()) return AutoSkipMatchResult(rule, selectorMatch.node, points)
                }
                if (selectorMatch.attempted) return@forEach
            }
            val snapshot = nodeSnapshot ?: AutoSkipNodeSnapshot(root).also { nodeSnapshot = it }
            findMatchingNode(snapshot, rule)?.let { ref ->
                val points = tapPointResolver.resolve(ref, rule.action.toTapRequest())
                    .map { point -> Point(point.x, point.y) }
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
        if (!bounds.isReasonableForScreen(screenWidth, screenHeight)) return false
        if (rule.match.region?.contains(bounds, screenWidth, screenHeight) == false) return false
        if (matchesExcludedField(ref, rule.match)) return false
        return matchesPositiveField(ref, rule.match)
    }

    private fun matchesExcludedField(ref: AutoSkipNodeRef, match: AutoSkipMatch): Boolean {
        if (match.normalizedExcludeText.isNotEmpty() && matchesAny(ref.text, match.normalizedExcludeText)) return true
        if (match.normalizedExcludeDesc.isNotEmpty() && matchesAny(ref.desc, match.normalizedExcludeDesc)) return true
        if (match.normalizedExcludeResourceId.isNotEmpty() && matchesAny(ref.resourceId, match.normalizedExcludeResourceId)) return true
        return false
    }

    private fun matchesPositiveField(ref: AutoSkipNodeRef, match: AutoSkipMatch): Boolean {
        if (match.normalizedText.isNotEmpty() && matchesAny(ref.text, match.normalizedText)) return true
        if (match.normalizedDesc.isNotEmpty() && matchesAny(ref.desc, match.normalizedDesc)) return true
        if (match.normalizedResourceId.isNotEmpty() && matchesAny(ref.resourceId, match.normalizedResourceId)) return true
        if (match.normalizedClassName.isNotEmpty() && matchesAny(ref.className, match.normalizedClassName)) return true
        return false
    }

    private fun matchesAny(value: String?, patterns: List<String>): Boolean {
        if (patterns.isEmpty()) return true
        val target = value?.trim().orEmpty()
        if (target.isEmpty()) return false
        return patterns.any { pattern ->
            target.equals(pattern, ignoreCase = true) || target.contains(pattern, ignoreCase = true)
        }
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
                bounds = node.boundsInScreen(),
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
        override val parent: AutoSkipNodeRef?,
        val bounds: Rect,
        val text: String?,
        val desc: String?,
        val resourceId: String?,
        val className: String?,
        val visibleToUser: Boolean,
        override val clickable: Boolean
    ) : AutoSkipTapNode {
        override val tapBounds = bounds.toTapBounds()
    }
}

internal interface AutoSkipTapNode {
    val parent: AutoSkipTapNode?
    val tapBounds: AutoSkipTapBounds
    val clickable: Boolean
}

internal data class AutoSkipTapBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
) {
    fun isReasonableForScreen(screenWidth: Int, screenHeight: Int): Boolean {
        return isReasonableBounds(left, top, right, bottom, screenWidth, screenHeight)
    }

    fun pointAt(xRatio: Float, yRatio: Float): AutoSkipTapPoint {
        return AutoSkipTapPoint(
            (left + (right - left) * xRatio.coerceIn(0f, 1f)).toInt(),
            (top + (bottom - top) * yRatio.coerceIn(0f, 1f)).toInt()
        )
    }
}

internal data class AutoSkipTapPoint(val x: Int, val y: Int)

internal enum class AutoSkipTapMode {
    CENTER,
    TOP_RIGHT,
    BOTTOM_RIGHT,
    CUSTOM_RATIO,
    PROBE
}

internal data class AutoSkipTapRequest(
    val mode: AutoSkipTapMode,
    val customXRatio: Float = 0.5f,
    val customYRatio: Float = 0.5f
)

internal class AutoSkipTapPointResolver(
    private val screenWidth: Int,
    private val screenHeight: Int
) {
    fun resolve(node: AutoSkipTapNode, request: AutoSkipTapRequest): List<AutoSkipTapPoint> {
        val bounds = clickableBounds(node)
        if (!bounds.isReasonableForScreen(screenWidth, screenHeight)) return emptyList()
        return when (request.mode) {
            AutoSkipTapMode.CENTER -> listOf(bounds.pointAt(0.5f, 0.5f))
            AutoSkipTapMode.TOP_RIGHT -> listOf(bounds.pointAt(0.86f, 0.28f))
            AutoSkipTapMode.BOTTOM_RIGHT -> listOf(bounds.pointAt(0.86f, 0.72f))
            AutoSkipTapMode.CUSTOM_RATIO -> listOf(bounds.pointAt(request.customXRatio, request.customYRatio))
            AutoSkipTapMode.PROBE -> probePoints(node)
        }.filter { point -> point.x in 0 until screenWidth && point.y in 0 until screenHeight }
            .distinctBy { it.x to it.y }
    }

    private fun probePoints(node: AutoSkipTapNode): List<AutoSkipTapPoint> {
        val points = ArrayList<AutoSkipTapPoint>()
        val own = node.tapBounds
        if (own.isReasonableForScreen(screenWidth, screenHeight)) {
            points.add(own.pointAt(0.5f, 0.5f))
            points.add(own.pointAt(0.86f, 0.32f))
            points.add(own.pointAt(0.86f, 0.68f))
        }
        var parent = node.parent
        var depth = 0
        while (parent != null && depth < 3) {
            val bounds = parent.tapBounds
            if (bounds.isReasonableForScreen(screenWidth, screenHeight)) {
                points.add(bounds.pointAt(0.5f, 0.5f))
                points.add(bounds.pointAt(0.86f, 0.32f))
            }
            if (parent.clickable) break
            parent = parent.parent
            depth += 1
        }
        return points
    }

    private fun clickableBounds(node: AutoSkipTapNode): AutoSkipTapBounds {
        var candidate: AutoSkipTapNode? = node
        var depth = 0
        while (candidate != null && depth < 3) {
            val bounds = candidate.tapBounds
            if (candidate.clickable && bounds.isReasonableForScreen(screenWidth, screenHeight)) return bounds
            candidate = candidate.parent
            depth += 1
        }
        return node.tapBounds
    }
}

private class AccessibilityTapNode(private val node: AccessibilityNodeInfo) : AutoSkipTapNode {
    override val parent: AutoSkipTapNode?
        get() = node.parent?.let(::AccessibilityTapNode)
    override val tapBounds: AutoSkipTapBounds
        get() = node.boundsInScreen().toTapBounds()
    override val clickable: Boolean
        get() = node.isClickable
}

private fun Rect.toTapBounds(): AutoSkipTapBounds {
    return AutoSkipTapBounds(left, top, right, bottom)
}

private fun AutoSkipAction.toTapRequest(): AutoSkipTapRequest {
    return AutoSkipTapRequest(
        mode = AutoSkipTapMode.valueOf(tapStrategy.name),
        customXRatio = customXRatio,
        customYRatio = customYRatio
    )
}

data class AutoSkipMatchResult(
    val rule: AutoSkipRule,
    val node: AccessibilityNodeInfo,
    val points: List<Point>
)

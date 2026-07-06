package com.hujiayucc.hook.autoskip

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import li.songe.selector.MatchOption
import li.songe.selector.QueryContext
import li.songe.selector.Selector
import li.songe.selector.Transform
import li.songe.selector.getBooleanInvoke
import li.songe.selector.getCharSequenceAttr
import li.songe.selector.getCharSequenceInvoke
import li.songe.selector.getIntInvoke

internal class GkdOfficialSelectorMatcher(
    private val screenWidth: Int,
    private val screenHeight: Int,
    private val fallback: GkdSelectorMatcher
) {
    private val selectorCache = HashMap<String, Selector?>()
    private val option = MatchOption.default

    fun findFirst(
        root: AccessibilityNodeInfo,
        selectors: List<String>,
        visible: Boolean,
        region: AutoSkipRegion?
    ): GkdSelectorMatch {
        if (selectors.isEmpty()) return GkdSelectorMatch(null, attempted = false)
        val nodeTree = GkdAccessibilityNodeTree(root)
        var attempted = false
        selectors.forEach { rawSelector ->
            val selectorText = rawSelector.trim()
            if (selectorText.isBlank()) return@forEach
            val selector = selector(selectorText)
            if (selector == null) {
                val fallbackMatch = fallback.findFirst(root, listOf(selectorText), visible, region)
                attempted = attempted || fallbackMatch.attempted
                if (fallbackMatch.node != null) return fallbackMatch
                return@forEach
            }
            attempted = true
            val officialMatch = runCatching {
                querySelfOrSelectorAll(nodeTree.root, selector)
                    .firstOrNull { ref -> isUsable(ref.node, visible, region) }
            }.getOrElse {
                val fallbackMatch = fallback.findFirst(root, listOf(selectorText), visible, region)
                attempted = attempted || fallbackMatch.attempted
                if (fallbackMatch.node != null) return fallbackMatch
                null
            }
            if (officialMatch != null) return GkdSelectorMatch(officialMatch.node, attempted = true)
        }
        return GkdSelectorMatch(null, attempted)
    }

    fun hasAny(root: AccessibilityNodeInfo, selectors: List<String>): Boolean {
        if (selectors.isEmpty()) return false
        val nodeTree = GkdAccessibilityNodeTree(root)
        return selectors.any { rawSelector ->
            val selectorText = rawSelector.trim()
            if (selectorText.isBlank()) return@any false
            val selector = selector(selectorText)
            if (selector == null) return@any fallback.hasAny(root, listOf(selectorText))
            runCatching { querySelfOrSelectorAll(nodeTree.root, selector).firstOrNull() != null }
                .getOrElse { fallback.hasAny(root, listOf(selectorText)) }
        }
    }

    private fun selector(source: String): Selector? {
        return selectorCache.getOrPut(source) { Selector.parseOrNull(source) }
    }

    private fun querySelfOrSelectorAll(
        root: GkdAccessibilityNodeRef,
        selector: Selector
    ): Sequence<GkdAccessibilityNodeRef> = sequence {
        if (selector.isMatchRoot) {
            selector.match(root, transform, option)?.let { yield(it) }
            return@sequence
        }
        selector.match(root, transform, option)?.let { yield(it) }
        yieldAll(transform.querySelectorAll(root, selector, option))
    }

    private fun isUsable(node: AccessibilityNodeInfo, visible: Boolean, region: AutoSkipRegion?): Boolean {
        val bounds = node.bounds()
        if (visible && !node.isVisibleToUser) return false
        if (!isReasonableBounds(bounds)) return false
        if (region?.contains(bounds, screenWidth, screenHeight) == false) return false
        return true
    }

    private fun isReasonableBounds(bounds: Rect): Boolean {
        if (bounds.isEmpty) return false
        if (bounds.right <= 0 || bounds.bottom <= 0 || bounds.left >= screenWidth || bounds.top >= screenHeight) return false
        val area = bounds.width() * bounds.height()
        return bounds.width() >= 8 && bounds.height() >= 8 && area <= screenWidth * screenHeight * 0.6f
    }

    private companion object {
        private const val MAX_CHILD_SIZE = 512
        private const val MAX_DESCENDANTS_SIZE = 4096

        private val transform = Transform<GkdAccessibilityNodeRef>(
            getAttr = { target, name ->
                when (target) {
                    is QueryContext<*> -> when (name) {
                        "prev" -> target.prev
                        "current" -> target.current
                        else -> (target.current as? GkdAccessibilityNodeRef)?.let { getNodeAttr(it, name) }
                    }
                    is GkdAccessibilityNodeRef -> getNodeAttr(target, name)
                    is CharSequence -> getCharSequenceAttr(target, name)
                    else -> null
                }
            },
            getInvoke = { target, name, args ->
                when (target) {
                    is GkdAccessibilityNodeRef -> getNodeInvoke(target, name, args)
                    is QueryContext<*> -> getContextInvoke(target, name, args)
                    is CharSequence -> runCatching { getCharSequenceInvoke(target, name, args) }.getOrNull()
                    is Int -> runCatching { getIntInvoke(target, name, args) }.getOrNull()
                    is Boolean -> runCatching { getBooleanInvoke(target, name, args) }.getOrNull()
                    else -> null
                }
            },
            getName = { node -> node.node.className },
            getChildren = { node -> node.children.asSequence() },
            getParent = { node -> node.parent },
            getRoot = { node -> generateSequence(node) { it.parent }.lastOrNull() }
        )

        private fun getNodeAttr(ref: GkdAccessibilityNodeRef, name: String): Any? {
            val node = ref.node
            val bounds = node.bounds()
            return when (name) {
                "id" -> node.viewIdResourceName
                "vid" -> node.vid()
                "name", "className" -> node.className
                "text" -> node.text
                "desc", "description", "contentDescription" -> node.contentDescription
                "clickable" -> node.isClickable
                "focusable" -> node.isFocusable
                "checkable" -> node.isCheckable
                "checked" -> runCatching { node.isChecked }.getOrNull()
                "editable" -> node.isEditable
                "longClickable" -> node.isLongClickable
                "visibleToUser" -> node.isVisibleToUser
                "left" -> bounds.left
                "top" -> bounds.top
                "right" -> bounds.right
                "bottom" -> bounds.bottom
                "width" -> bounds.width()
                "height" -> bounds.height()
                "childCount" -> node.childCount
                "index" -> ref.index
                "depth" -> ref.depth
                "parent" -> ref.parent
                else -> null
            }
        }

        private fun getNodeInvoke(ref: GkdAccessibilityNodeRef, name: String, args: List<Any>): Any? {
            return when (name) {
                "getChild" -> args.firstInt()?.let { ref.children.getOrNull(it) }
                else -> null
            }
        }

        private fun getContextInvoke(context: QueryContext<*>, name: String, args: List<Any>): Any? {
            return when (name) {
                "getPrev" -> args.firstInt()?.let { context.getPrev(it) }
                "getChild" -> (context.current as? GkdAccessibilityNodeRef)?.let { getNodeInvoke(it, name, args) }
                else -> null
            }
        }

        private fun List<Any>.firstInt(): Int? = firstOrNull() as? Int

        private fun AccessibilityNodeInfo.vid(): CharSequence? {
            val id = viewIdResourceName ?: return null
            val appId = packageName ?: return null
            if (!id.startsWith(appId)) return null
            val prefix = ":id/"
            if (!id.startsWith(prefix, appId.length)) return null
            return id.subSequence(appId.length + prefix.length, id.length)
        }
    }
}

private class GkdAccessibilityNodeTree(root: AccessibilityNodeInfo) {
    private var nextOrder = 0
    val root: GkdAccessibilityNodeRef = append(root, parent = null, index = 0, depth = 0)

    private fun append(
        node: AccessibilityNodeInfo,
        parent: GkdAccessibilityNodeRef?,
        index: Int,
        depth: Int
    ): GkdAccessibilityNodeRef {
        val ref = GkdAccessibilityNodeRef(node, parent, index, depth, nextOrder++)
        if (nextOrder > MAX_DESCENDANTS_SIZE) return ref
        val childLimit = node.childCount.coerceAtMost(MAX_CHILD_SIZE)
        for (childIndex in 0 until childLimit) {
            if (nextOrder > MAX_DESCENDANTS_SIZE) break
            val child = node.getChild(childIndex) ?: continue
            ref.children.add(append(child, ref, childIndex, depth + 1))
        }
        return ref
    }

    private companion object {
        private const val MAX_CHILD_SIZE = 512
        private const val MAX_DESCENDANTS_SIZE = 4096
    }
}

private class GkdAccessibilityNodeRef(
    val node: AccessibilityNodeInfo,
    val parent: GkdAccessibilityNodeRef?,
    val index: Int,
    val depth: Int,
    val order: Int
) {
    val children = ArrayList<GkdAccessibilityNodeRef>()
}

private fun AccessibilityNodeInfo.bounds(): Rect {
    val rect = Rect()
    getBoundsInScreen(rect)
    return rect
}

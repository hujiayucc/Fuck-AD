package com.hujiayucc.hook.autoskip

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

internal class GkdSelectorMatcher(
    private val screenWidth: Int,
    private val screenHeight: Int
) {
    private val expressionCache = HashMap<String, GkdSelectorExpression?>()

    fun findFirst(
        root: AccessibilityNodeInfo,
        selectors: List<String>,
        visible: Boolean,
        region: AutoSkipRegion?
    ): GkdSelectorMatch {
        if (selectors.isEmpty()) return GkdSelectorMatch(null, attempted = false)
        val nodeIndex = GkdNodeIndex(root)
        var attempted = false
        selectors.forEach { selector ->
            val expression = expression(selector) ?: return@forEach
            attempted = true
            expression.match(nodeIndex).firstOrNull { ref ->
                isUsable(ref.node, visible, region)
            }?.let { return GkdSelectorMatch(it.node, attempted = true) }
        }
        return GkdSelectorMatch(null, attempted)
    }

    fun hasAny(root: AccessibilityNodeInfo, selectors: List<String>): Boolean {
        if (selectors.isEmpty()) return false
        val nodeIndex = GkdNodeIndex(root)
        return selectors.any { selector ->
            expression(selector)?.match(nodeIndex).orEmpty().isNotEmpty()
        }
    }

    private fun expression(selector: String): GkdSelectorExpression? {
        val normalized = selector.trim()
        if (normalized.isBlank()) return null
        return expressionCache.getOrPut(normalized) {
            runCatching { GkdSelectorParser(normalized).parse() }.getOrNull()
        }
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
}

internal data class GkdSelectorMatch(
    val node: AccessibilityNodeInfo?,
    val attempted: Boolean
)

private class GkdNodeIndex(root: AccessibilityNodeInfo) {
    val nodes = ArrayList<GkdNodeRef>()

    init {
        append(root, null, 0, 0)
    }

    private fun append(node: AccessibilityNodeInfo, parent: GkdNodeRef?, index: Int, depth: Int): GkdNodeRef {
        val ref = GkdNodeRef(node, parent, index, depth, nodes.size)
        nodes.add(ref)
        if (nodes.size >= MAX_NODES) return ref
        for (childIndex in 0 until node.childCount) {
            val child = node.getChild(childIndex) ?: continue
            if (nodes.size >= MAX_NODES) break
            ref.children.add(append(child, ref, childIndex, depth + 1))
        }
        return ref
    }

    companion object {
        private const val MAX_NODES = 1200
    }
}

private class GkdNodeRef(
    val node: AccessibilityNodeInfo,
    val parent: GkdNodeRef?,
    val index: Int,
    val depth: Int,
    val order: Int
) {
    val children = ArrayList<GkdNodeRef>()
}

private class GkdEvalContext(
    val node: GkdNodeRef,
    val prev: GkdEvalContext?
)
private interface GkdSelectorExpression {
    fun match(index: GkdNodeIndex): List<GkdNodeRef> {
        val result = ArrayList<GkdNodeRef>()
        index.nodes.forEach { ref ->
            if (ref.parent == null) return@forEach
            match(index, GkdEvalContext(ref, null))?.let { result.add(it) }
        }
        return result.distinctBy { it.order }
    }

    fun match(index: GkdNodeIndex, context: GkdEvalContext): GkdNodeRef?
}

private class GkdOrExpression(private val expressions: List<GkdSelectorExpression>) : GkdSelectorExpression {
    override fun match(index: GkdNodeIndex, context: GkdEvalContext): GkdNodeRef? {
        for (expression in expressions) {
            val matched = expression.match(index, context)
            if (matched != null) return matched
        }
        return null
    }
}

private class GkdAndExpression(private val expressions: List<GkdSelectorExpression>) : GkdSelectorExpression {
    override fun match(index: GkdNodeIndex, context: GkdEvalContext): GkdNodeRef? {
        var result: GkdNodeRef? = null
        for (expression in expressions) {
            result = expression.match(index, context) ?: return null
        }
        return result
    }
}

private class GkdNotExpression(private val expression: GkdSelectorExpression) : GkdSelectorExpression {
    override fun match(index: GkdNodeIndex, context: GkdEvalContext): GkdNodeRef? {
        return if (expression.match(index, context) == null) context.node else null
    }
}

private class GkdSequenceExpression(
    private val attributes: List<GkdAttributeSelector>,
    private val relations: List<GkdRelationSelector>
) : GkdSelectorExpression {
    private val targetIndex = attributes.indexOfLast { it.target }.takeIf { it >= 0 } ?: attributes.lastIndex

    override fun match(index: GkdNodeIndex, context: GkdEvalContext): GkdNodeRef? {
        if (attributes.isEmpty()) return null
        val rightIndex = attributes.lastIndex
        if (!attributes[rightIndex].matches(context)) return null
        val target = if (rightIndex == targetIndex) context.node else null
        return matchPrevious(index, rightIndex, context.node, context, target)
    }

    private fun matchPrevious(
        index: GkdNodeIndex,
        attributeIndex: Int,
        current: GkdNodeRef,
        currentContext: GkdEvalContext,
        target: GkdNodeRef?
    ): GkdNodeRef? {
        if (attributeIndex == 0) return target ?: current
        val previousAttributeIndex = attributeIndex - 1
        val relation = relations[previousAttributeIndex]
        for (candidate in relation.candidates(current, currentContext, index)) {
            val candidateContext = GkdEvalContext(candidate, currentContext)
            if (attributes[previousAttributeIndex].matches(candidateContext)) {
                val matched = matchPrevious(
                    index,
                    previousAttributeIndex,
                    candidate,
                    candidateContext,
                    if (previousAttributeIndex == targetIndex) candidate else target
                )
                if (matched != null) return matched
            }
        }
        return null
    }
}

private class GkdAttributeSelector(
    val target: Boolean,
    private val className: String?,
    private val conditions: List<GkdBoolExpression>
) {
    fun matches(context: GkdEvalContext): Boolean {
        if (!matchesClass(context.node.node.className?.toString().orEmpty())) return false
        return conditions.all { it.eval(context) }
    }

    private fun matchesClass(actual: String): Boolean {
        val expected = className?.trim().orEmpty()
        if (expected.isBlank() || expected == "*") return true
        return actual.equals(expected, ignoreCase = false) ||
            actual.substringAfterLast('.').equals(expected, ignoreCase = false) ||
            actual.endsWith(".$expected", ignoreCase = false)
    }
}

private class GkdRelationSelector(
    private val operator: GkdRelationOperator,
    private val expression: GkdRelationExpression
) {
    fun candidates(current: GkdNodeRef, context: GkdEvalContext, index: GkdNodeIndex): List<GkdNodeRef> {
        return when (operator) {
            GkdRelationOperator.PREVIOUS_SIBLING -> siblingCandidates(current, -1)
            GkdRelationOperator.NEXT_SIBLING -> siblingCandidates(current, 1)
            GkdRelationOperator.ANCESTOR -> ancestorCandidates(current)
            GkdRelationOperator.CHILD -> childCandidates(current)
            GkdRelationOperator.DESCENDANT -> descendantCandidates(current)
            GkdRelationOperator.PREVIOUS_CONTEXT -> previousContextCandidates(context)
        }
    }

    private fun siblingCandidates(current: GkdNodeRef, direction: Int): List<GkdNodeRef> {
        val siblings = current.parent?.children ?: return emptyList()
        val candidates = if (direction < 0) {
            siblings.take(current.index).asReversed()
        } else {
            siblings.drop(current.index + 1)
        }
        return candidates.filterIndexed { offset, _ -> expression.matches(offset) }
    }

    private fun ancestorCandidates(current: GkdNodeRef): List<GkdNodeRef> {
        val result = ArrayList<GkdNodeRef>()
        var parent = current.parent
        var offset = 0
        while (parent != null) {
            if (expression.matches(offset)) result.add(parent)
            parent = parent.parent
            offset += 1
        }
        return result
    }

    private fun childCandidates(current: GkdNodeRef): List<GkdNodeRef> {
        return current.children.filterIndexed { offset, _ -> expression.matches(offset) }
    }

    private fun descendantCandidates(current: GkdNodeRef): List<GkdNodeRef> {
        val result = ArrayList<GkdNodeRef>()
        var offset = 0
        fun append(ref: GkdNodeRef) {
            ref.children.forEach { child ->
                if (expression.matches(offset)) result.add(child)
                offset += 1
                append(child)
            }
        }
        append(current)
        return result
    }

    private fun previousContextCandidates(context: GkdEvalContext): List<GkdNodeRef> {
        val result = ArrayList<GkdNodeRef>()
        var cursor = context.prev
        var offset = 0
        while (cursor != null) {
            if (expression.matches(offset)) result.add(cursor.node)
            cursor = cursor.prev
            offset += 1
        }
        return result
    }
}

private enum class GkdRelationOperator {
    PREVIOUS_SIBLING,
    NEXT_SIBLING,
    ANCESTOR,
    CHILD,
    DESCENDANT,
    PREVIOUS_CONTEXT
}

private class GkdRelationExpression(private val matcher: (Int) -> Boolean) {
    fun matches(offset: Int): Boolean = offset >= 0 && matcher(offset)

    companion object {
        fun defaultFor(operator: GkdRelationOperator, implicitDescendant: Boolean): GkdRelationExpression {
            if (implicitDescendant && operator == GkdRelationOperator.ANCESTOR) return parse("n")
            return parse("1")
        }

        fun parse(raw: String): GkdRelationExpression {
            val text = raw.trim().ifBlank { "1" }
            val inner = if (text.startsWith("(") && text.endsWith(")")) {
                text.substring(1, text.length - 1).trim()
            } else {
                text
            }
            if (inner.contains(',')) {
                val values = ArrayList<Int>()
                for (part in inner.split(',')) {
                    values.add(part.trim().toIntOrNull() ?: return GkdRelationExpression { false })
                }
                if (values.isEmpty() || values.any { it <= 0 } || values.zipWithNext().any { it.first >= it.second }) {
                    return GkdRelationExpression { false }
                }
                val offsets = values.map { it - 1 }.toSet()
                return GkdRelationExpression { offset -> offsets.contains(offset) }
            }
            val polynomial = parsePolynomial(inner) ?: return GkdRelationExpression { false }
            return fromPolynomial(polynomial.first, polynomial.second)
        }

        private fun fromPolynomial(a: Int, b: Int): GkdRelationExpression {
            val expression = runCatching { GkdPolynomial(a, b) }.getOrNull()
                ?: return GkdRelationExpression { false }
            return GkdRelationExpression { offset -> expression.matches(offset) }
        }

        private data class GkdPolynomial(val a: Int, val b: Int) {
            val minOffset: Int
            val maxOffset: Int?

            init {
                val minValue = when {
                    a > 0 -> when {
                        b > 0 -> a + b
                        b == 0 -> a
                        else -> a * (-b / a + 1) + b
                    }
                    a == 0 -> if (b > 0) b else invalid()
                    b > 0 && b > -a -> {
                        val maxN = -b / a - if (b % a == 0) 1 else 0
                        a * maxN + b
                    }
                    else -> invalid()
                }
                val maxValue = when {
                    a > 0 -> null
                    a == 0 -> if (b > 0) b else invalid()
                    b > 0 && b > -a -> a + b
                    else -> invalid()
                }
                minOffset = minValue - 1
                maxOffset = maxValue?.minus(1)
            }

            fun matches(offset: Int): Boolean {
                if (offset < minOffset) return false
                maxOffset?.let { if (offset > it) return false }
                if (minOffset == maxOffset) return offset == minOffset
                val y = offset + 1 - b
                return y % a == 0 && y / a >= 1
            }

            private fun invalid(): Nothing = error("Invalid relation expression")
        }

        private fun parsePolynomial(text: String): Pair<Int, Int>? {
            val compact = text.replace(" ", "")
            if (compact.isBlank()) return 0 to 1
            val tokens = ArrayList<String>()
            var start = 0
            for (index in 1 until compact.length) {
                if (compact[index] == '+' || compact[index] == '-') {
                    tokens.add(compact.substring(start, index))
                    start = index
                }
            }
            tokens.add(compact.substring(start))
            var a: Int? = null
            var b: Int? = null
            for (token in tokens) {
                if (token.isBlank()) return null
                val sign = if (token.startsWith("-")) -1 else 1
                val body = token.removePrefix("+").removePrefix("-")
                if (body.isBlank()) return null
                if (body.endsWith("n")) {
                    if (a != null) return null
                    val coefficientText = body.dropLast(1)
                    val coefficient = if (coefficientText.isBlank()) 1 else coefficientText.toIntOrNull() ?: return null
                    a = sign * coefficient
                } else {
                    if (b != null) return null
                    b = sign * (body.toIntOrNull() ?: return null)
                }
            }
            return (a ?: 0) to (b ?: 0)
        }
    }
}


private interface GkdBoolExpression {
    fun eval(context: GkdEvalContext): Boolean
}

private class GkdLogicalBoolExpression(
    private val operator: String,
    private val left: GkdBoolExpression,
    private val right: GkdBoolExpression
) : GkdBoolExpression {
    override fun eval(context: GkdEvalContext): Boolean {
        return if (operator == "&&") left.eval(context) && right.eval(context) else left.eval(context) || right.eval(context)
    }
}

private class GkdNotBoolExpression(private val expression: GkdBoolExpression) : GkdBoolExpression {
    override fun eval(context: GkdEvalContext): Boolean = !expression.eval(context)
}

private class GkdComparisonExpression(
    private val left: GkdValueExpression,
    private val operator: String?,
    private val right: GkdValueExpression?
) : GkdBoolExpression {
    override fun eval(context: GkdEvalContext): Boolean {
        val leftValue = left.eval(context)
        if (operator == null) return leftValue.asBoolean() == true
        val rightValue = right?.eval(context) ?: GkdValue.NullValue
        return compareValues(leftValue, operator, rightValue)
    }

    private fun compareValues(left: GkdValue, operator: String, right: GkdValue): Boolean {
        return when (operator) {
            "=", "==" -> left.sameValue(right)
            "!=" -> !left.sameValue(right)
            ">" -> compareInts(left, right) { l, r -> l > r }
            ">=" -> compareInts(left, right) { l, r -> l >= r }
            "<" -> compareInts(left, right) { l, r -> l < r }
            "<=" -> compareInts(left, right) { l, r -> l <= r }
            "^=" -> compareStrings(left, right) { l, r -> l.startsWith(r) }
            "!^=" -> compareStrings(left, right) { l, r -> !l.startsWith(r) }
            "*=" -> compareStrings(left, right) { l, r -> l.contains(r) }
            "!*=" -> compareStrings(left, right) { l, r -> !l.contains(r) }
            "$=" -> compareStrings(left, right) { l, r -> l.endsWith(r) }
            "!$=" -> compareStrings(left, right) { l, r -> !l.endsWith(r) }
            "~=" -> matchesRegex(left, right, false)
            "!~=" -> matchesRegex(left, right, true)
            else -> false
        }
    }

    private fun compareInts(left: GkdValue, right: GkdValue, predicate: (Int, Int) -> Boolean): Boolean {
        val leftValue = (left as? GkdValue.IntValue)?.value ?: return false
        val rightValue = (right as? GkdValue.IntValue)?.value ?: return false
        return predicate(leftValue, rightValue)
    }

    private fun compareStrings(left: GkdValue, right: GkdValue, predicate: (String, String) -> Boolean): Boolean {
        val leftValue = (left as? GkdValue.StringValue)?.value ?: return false
        val rightValue = (right as? GkdValue.StringValue)?.value ?: return false
        return predicate(leftValue, rightValue)
    }

    private fun matchesRegex(left: GkdValue, right: GkdValue, negate: Boolean): Boolean {
        val value = (left as? GkdValue.StringValue)?.value ?: return false
        val pattern = (right as? GkdValue.StringValue)?.value ?: return false
        val matched = runCatching { Regex(pattern).matches(value) }.getOrDefault(false)
        return if (negate) !matched else matched
    }
}

private interface GkdValueExpression {
    fun eval(context: GkdEvalContext): GkdValue
}

private class GkdLiteralExpression(private val value: GkdValue) : GkdValueExpression {
    override fun eval(context: GkdEvalContext): GkdValue = value
}

private class GkdIdentifierExpression(private val name: String) : GkdValueExpression {
    override fun eval(context: GkdEvalContext): GkdValue = context.resolveIdentifier(name)
}

private class GkdMemberExpression(
    private val owner: GkdValueExpression,
    private val name: String
) : GkdValueExpression {
    override fun eval(context: GkdEvalContext): GkdValue = owner.eval(context).member(name)
}

private class GkdCallExpression(
    private val owner: GkdValueExpression?,
    private val name: String,
    private val args: List<GkdValueExpression>
) : GkdValueExpression {
    override fun eval(context: GkdEvalContext): GkdValue {
        val ownerValue = owner?.eval(context)
        if (ownerValue is GkdValue.BoolValue) return ownerValue.callLazy(name, args, context)
        val values = args.map { it.eval(context) }
        return ownerValue?.call(name, values) ?: context.callGlobal(name, values)
    }

    private fun GkdValue.BoolValue.callLazy(
        name: String,
        args: List<GkdValueExpression>,
        context: GkdEvalContext
    ): GkdValue {
        return when (name) {
            "or" -> if (value) GkdValue.BoolValue(true) else args.firstOrNull()?.eval(context)?.asBoolean()?.let { GkdValue.BoolValue(it) } ?: GkdValue.NullValue
            "and" -> if (!value) GkdValue.BoolValue(false) else args.firstOrNull()?.eval(context)?.asBoolean()?.let { GkdValue.BoolValue(it) } ?: GkdValue.NullValue
            "ifElse" -> if (value) args.getOrNull(0)?.eval(context) ?: GkdValue.NullValue else args.getOrNull(1)?.eval(context) ?: GkdValue.NullValue
            else -> call(name, args.map { it.eval(context) })
        }
    }
}

private sealed class GkdValue {
    object NullValue : GkdValue()
    data class BoolValue(val value: Boolean) : GkdValue()
    data class IntValue(val value: Int) : GkdValue()
    data class StringValue(val value: String) : GkdValue()
    data class NodeValue(val value: GkdNodeRef?) : GkdValue()
    data class ContextValue(val value: GkdEvalContext?) : GkdValue()

    fun asBoolean(): Boolean? = when (this) {
        is BoolValue -> value
        else -> null
    }

    fun asInt(): Int? = when (this) {
        is IntValue -> value
        else -> null
    }

    fun asString(): String? = when (this) {
        is StringValue -> value
        is IntValue -> value.toString()
        is BoolValue -> value.toString()
        NullValue -> null
        is NodeValue -> value?.node?.className?.toString()
        is ContextValue -> value?.node?.node?.className?.toString()
    }

    fun sameValue(other: GkdValue): Boolean {
        if (isNullLike() || other.isNullLike()) return isNullLike() && other.isNullLike()
        return when {
            this is BoolValue && other is BoolValue -> value == other.value
            this is IntValue && other is IntValue -> value == other.value
            this is StringValue && other is StringValue -> value == other.value
            this is NodeValue && other is NodeValue -> value?.order == other.value?.order
            this is ContextValue && other is ContextValue -> sameContext(value, other.value)
            else -> false
        }
    }

    private fun sameContext(left: GkdEvalContext?, right: GkdEvalContext?): Boolean {
        var leftCursor = left
        var rightCursor = right
        while (leftCursor != null && rightCursor != null) {
            if (leftCursor.node.order != rightCursor.node.order) return false
            leftCursor = leftCursor.prev
            rightCursor = rightCursor.prev
        }
        return leftCursor == null && rightCursor == null
    }

    private fun isNullLike(): Boolean {
        return when (this) {
            is NullValue -> true
            is NodeValue -> value == null
            is ContextValue -> value == null
            else -> false
        }
    }

    fun member(name: String): GkdValue {
        return when (this) {
            is NodeValue -> value.nodeMember(name)
            is ContextValue -> value.contextMember(name)
            is StringValue -> if (name == "length") IntValue(value.length) else NullValue
            else -> NullValue
        }
    }

    fun call(name: String, args: List<GkdValue>): GkdValue {
        return when (this) {
            is NodeValue -> value.nodeCall(name, args)
            is ContextValue -> value.contextCall(name, args)
            is StringValue -> stringCall(value, name, args)
            is IntValue -> intCall(value, name, args)
            is BoolValue -> boolCall(value, name, args)
            else -> NullValue
        }
    }

    private fun GkdNodeRef?.nodeMember(name: String): GkdValue {
        val ref = this ?: return NullValue
        val node = ref.node
        return when (name) {
            "_id", "_pid" -> NullValue
            "id" -> node.viewIdResourceName?.let { StringValue(it) } ?: NullValue
            "vid" -> node.viewIdResourceName?.substringAfterLast('/')?.let { StringValue(it) } ?: NullValue
            "name", "className" -> node.className?.toString()?.let { StringValue(it) } ?: NullValue
            "text" -> node.text?.toString()?.let { StringValue(it) } ?: NullValue
            "desc", "description", "contentDescription" -> node.contentDescription?.toString()?.let { StringValue(it) } ?: NullValue
            "clickable" -> BoolValue(node.isClickable)
            "focusable" -> BoolValue(node.isFocusable)
            "checkable" -> BoolValue(node.isCheckable)
            "checked" -> BoolValue(node.isChecked)
            "editable" -> BoolValue(node.isEditable)
            "longClickable" -> BoolValue(node.isLongClickable)
            "visibleToUser" -> BoolValue(node.isVisibleToUser)
            "left" -> IntValue(node.bounds().left)
            "top" -> IntValue(node.bounds().top)
            "right" -> IntValue(node.bounds().right)
            "bottom" -> IntValue(node.bounds().bottom)
            "width" -> IntValue(node.bounds().width())
            "height" -> IntValue(node.bounds().height())
            "childCount" -> IntValue(node.childCount)
            "index" -> IntValue(ref.index)
            "depth" -> IntValue(ref.depth)
            "parent" -> NodeValue(ref.parent)
            else -> NullValue
        }
    }

    private fun GkdEvalContext?.contextMember(name: String): GkdValue {
        val context = this ?: return NullValue
        return when (name) {
            "prev" -> ContextValue(context.prev)
            "current" -> NodeValue(context.node)
            else -> context.node.nodeMember(name)
        }
    }

    private fun GkdNodeRef?.nodeCall(name: String, args: List<GkdValue>): GkdValue {
        val ref = this ?: return NullValue
        return when (name) {
            "getChild" -> args.firstOrNull()?.asInt()?.let { index -> NodeValue(ref.children.getOrNull(index)) } ?: NullValue
            else -> NullValue
        }
    }

    private fun GkdEvalContext?.contextCall(name: String, args: List<GkdValue>): GkdValue {
        val context = this ?: return NullValue
        return when (name) {
            "getPrev" -> {
                val index = args.firstOrNull()?.asInt() ?: return NullValue
                if (index < 0) return NullValue
                var cursor = context.prev
                var remaining = index
                while (cursor != null && remaining > 0) {
                    cursor = cursor.prev
                    remaining -= 1
                }
                ContextValue(cursor ?: return NullValue)
            }
            else -> context.node.nodeCall(name, args)
        }
    }

    private fun stringCall(value: String, name: String, args: List<GkdValue>): GkdValue {
        return when (name) {
            "get" -> {
                val index = args.firstOrNull()?.asInt() ?: return NullValue
                StringValue(value.getOrNull(index).toString())
            }
            "at" -> {
                val rawIndex = args.firstOrNull()?.asInt() ?: return NullValue
                val index = if (rawIndex < 0) value.length + rawIndex else rawIndex
                StringValue(value.getOrNull(index).toString())
            }
            "substring" -> {
                val start = args.getOrNull(0)?.asInt() ?: return NullValue
                if (start < 0) return NullValue
                if (start >= value.length) return StringValue("")
                when (args.size) {
                    1 -> StringValue(value.substring(start, value.length))
                    2 -> {
                        val end = args.getOrNull(1)?.asInt() ?: return NullValue
                        if (end < start) NullValue else StringValue(value.substring(start, end.coerceAtMost(value.length)))
                    }
                    else -> NullValue
                }
            }
            "toInt" -> when (args.size) {
                0 -> value.toIntOrNull()?.let { IntValue(it) } ?: NullValue
                1 -> {
                    val radix = args.firstOrNull()?.asInt() ?: return NullValue
                    if (radix !in 2..36) NullValue else value.toIntOrNull(radix)?.let { IntValue(it) } ?: NullValue
                }
                else -> NullValue
            }
            "indexOf" -> when (args.size) {
                1 -> {
                    val needle = (args[0] as? StringValue)?.value ?: return NullValue
                    IntValue(value.indexOf(needle))
                }
                2 -> {
                    val needle = (args[0] as? StringValue)?.value ?: return NullValue
                    val start = args.getOrNull(1)?.asInt() ?: return NullValue
                    IntValue(value.indexOf(needle, start))
                }
                else -> NullValue
            }
            else -> NullValue
        }
    }

    private fun intCall(value: Int, name: String, args: List<GkdValue>): GkdValue {
        return when (name) {
            "toString" -> {
                val radix = args.firstOrNull()?.asInt()
                if (radix == null) StringValue(value.toString()) else if (radix in 2..36) StringValue(value.toString(radix)) else NullValue
            }
            "plus" -> args.firstOrNull()?.asInt()?.let { IntValue(value + it) } ?: NullValue
            "minus" -> args.firstOrNull()?.asInt()?.let { IntValue(value - it) } ?: NullValue
            "times" -> args.firstOrNull()?.asInt()?.let { IntValue(value * it) } ?: NullValue
            "div" -> args.firstOrNull()?.asInt()?.takeIf { it != 0 }?.let { IntValue(value / it) } ?: NullValue
            "rem" -> args.firstOrNull()?.asInt()?.takeIf { it != 0 }?.let { IntValue(value % it) } ?: NullValue
            "more" -> args.firstOrNull()?.asInt()?.let { BoolValue(value > it) } ?: NullValue
            "moreEqual" -> args.firstOrNull()?.asInt()?.let { BoolValue(value >= it) } ?: NullValue
            "less" -> args.firstOrNull()?.asInt()?.let { BoolValue(value < it) } ?: NullValue
            "lessEqual" -> args.firstOrNull()?.asInt()?.let { BoolValue(value <= it) } ?: NullValue
            else -> NullValue
        }
    }

    private fun boolCall(value: Boolean, name: String, args: List<GkdValue>): GkdValue {
        return when (name) {
            "toInt" -> IntValue(if (value) 1 else 0)
            "or" -> BoolValue(value || (args.firstOrNull()?.asBoolean() == true))
            "and" -> BoolValue(value && (args.firstOrNull()?.asBoolean() == true))
            "not" -> BoolValue(!value)
            "ifElse" -> if (value) args.getOrNull(0) ?: NullValue else args.getOrNull(1) ?: NullValue
            else -> NullValue
        }
    }
}

private fun GkdEvalContext.resolveIdentifier(name: String): GkdValue {
    return when (name) {
        "prev" -> GkdValue.ContextValue(prev)
        "current" -> GkdValue.NodeValue(node)
        else -> GkdValue.ContextValue(this).member(name)
    }
}

private fun GkdEvalContext.callGlobal(name: String, args: List<GkdValue>): GkdValue {
    return when (name) {
        "equal" -> GkdValue.BoolValue(args.getOrNull(0)?.sameValue(args.getOrNull(1) ?: GkdValue.NullValue) == true)
        "notEqual" -> GkdValue.BoolValue(args.getOrNull(0)?.sameValue(args.getOrNull(1) ?: GkdValue.NullValue) == false)
        "getChild", "getPrev" -> GkdValue.ContextValue(this).call(name, args)
        else -> GkdValue.NullValue
    }
}

private class GkdSelectorParser(private val source: String) {
    fun parse(): GkdSelectorExpression {
        return parseSelectorExpression(source.trim())
    }

    private fun parseSelectorExpression(text: String): GkdSelectorExpression {
        val orParts = splitTopLevel(text, "||")
        if (orParts.size > 1) return GkdOrExpression(orParts.map { parseSelectorExpression(it) })
        val andParts = splitTopLevel(text, "&&")
        if (andParts.size > 1) return GkdAndExpression(andParts.map { parseSelectorExpression(it) })
        val trimmed = text.trim()
        if (trimmed.startsWith("!(") && trimmed.endsWith(")") && enclosesWhole(trimmed, 1)) {
            return GkdNotExpression(parseSelectorExpression(trimmed.substring(2, trimmed.length - 1)))
        }
        val unwrapped = if (trimmed.startsWith("(") && trimmed.endsWith(")") && enclosesWhole(trimmed, 0)) {
            trimmed.substring(1, trimmed.length - 1).trim()
        } else {
            trimmed
        }
        return parseSequence(unwrapped)
    }

    private fun parseSequence(text: String): GkdSelectorExpression {
        val tokens = splitSelectorTokens(text)
        val attributes = ArrayList<GkdAttributeSelector>()
        val relations = ArrayList<GkdRelationSelector>()
        var index = 0
        attributes.add(parseAttribute(tokens.getOrNull(index) ?: error("Missing selector")))
        index += 1
        while (index < tokens.size) {
            val token = tokens[index]
            val relation = parseRelation(token)
            if (relation == null) {
                relations.add(GkdRelationSelector(GkdRelationOperator.ANCESTOR, GkdRelationExpression.parse("n")))
                attributes.add(parseAttribute(token))
                index += 1
            } else {
                relations.add(relation)
                index += 1
                attributes.add(parseAttribute(tokens.getOrNull(index) ?: error("Missing selector after relation")))
                index += 1
            }
        }
        return GkdSequenceExpression(attributes, relations)
    }

    private fun parseAttribute(raw: String): GkdAttributeSelector {
        var text = raw.trim()
        val target = text.startsWith("@")
        if (target) text = text.drop(1)
        val firstBracket = text.indexOf('[').takeIf { it >= 0 } ?: text.length
        val className = text.substring(0, firstBracket).trim().ifBlank { null }
        val conditions = ArrayList<GkdBoolExpression>()
        var index = firstBracket
        while (index < text.length) {
            if (text[index] != '[') error("Invalid attribute selector")
            val end = findClosing(text, index, '[', ']')
            conditions.add(GkdBoolParser(text.substring(index + 1, end)).parse())
            index = end + 1
        }
        return GkdAttributeSelector(target, className, conditions)
    }

    private fun parseRelation(raw: String): GkdRelationSelector? {
        val text = raw.trim()
        val pairs = listOf(
            "<<" to GkdRelationOperator.DESCENDANT,
            "->" to GkdRelationOperator.PREVIOUS_CONTEXT,
            "+" to GkdRelationOperator.PREVIOUS_SIBLING,
            "-" to GkdRelationOperator.NEXT_SIBLING,
            ">" to GkdRelationOperator.ANCESTOR,
            "<" to GkdRelationOperator.CHILD
        )
        val pair = pairs.firstOrNull { text.startsWith(it.first) } ?: return null
        val exprText = text.removePrefix(pair.first)
        val expr = if (exprText.isBlank()) {
            GkdRelationExpression.defaultFor(pair.second, false)
        } else {
            GkdRelationExpression.parse(exprText)
        }
        return GkdRelationSelector(pair.second, expr)
    }

    private fun splitSelectorTokens(text: String): List<String> {
        val tokens = ArrayList<String>()
        var start = 0
        var quote: Char? = null
        var bracketDepth = 0
        var parenDepth = 0
        var index = 0
        while (index < text.length) {
            val c = text[index]
            if (quote != null) {
                if (c == '\\') index += 1 else if (c == quote) quote = null
            } else {
                when (c) {
                    '\'', '"', '`' -> quote = c
                    '[' -> bracketDepth += 1
                    ']' -> bracketDepth -= 1
                    '(' -> parenDepth += 1
                    ')' -> parenDepth -= 1
                    ' ', '\n', '\r', '\t' -> if (bracketDepth == 0 && parenDepth == 0) {
                        val token = text.substring(start, index).trim()
                        if (token.isNotEmpty()) tokens.add(token)
                        start = index + 1
                    }
                }
            }
            index += 1
        }
        val tail = text.substring(start).trim()
        if (tail.isNotEmpty()) tokens.add(tail)
        return tokens
    }

    private fun splitTopLevel(text: String, operator: String): List<String> {
        val parts = ArrayList<String>()
        var start = 0
        var quote: Char? = null
        var bracketDepth = 0
        var parenDepth = 0
        var index = 0
        while (index <= text.length - operator.length) {
            val c = text[index]
            if (quote != null) {
                if (c == '\\') index += 1 else if (c == quote) quote = null
            } else {
                when (c) {
                    '\'', '"', '`' -> quote = c
                    '[' -> bracketDepth += 1
                    ']' -> bracketDepth -= 1
                    '(' -> parenDepth += 1
                    ')' -> parenDepth -= 1
                }
                if (bracketDepth == 0 && parenDepth == 0 && text.startsWith(operator, index)) {
                    parts.add(text.substring(start, index).trim())
                    start = index + operator.length
                    index += operator.length - 1
                }
            }
            index += 1
        }
        if (parts.isEmpty()) return listOf(text)
        parts.add(text.substring(start).trim())
        return parts.filter { it.isNotEmpty() }
    }

    private fun enclosesWhole(text: String, openIndex: Int): Boolean {
        var quote: Char? = null
        var depth = 0
        for (index in openIndex until text.length) {
            val c = text[index]
            if (quote != null) {
                if (c == '\\') continue else if (c == quote) quote = null
            } else {
                when (c) {
                    '\'', '"', '`' -> quote = c
                    '(' -> depth += 1
                    ')' -> {
                        depth -= 1
                        if (depth == 0 && index != text.lastIndex) return false
                    }
                }
            }
        }
        return depth == 0
    }

    private fun findClosing(text: String, openIndex: Int, open: Char, close: Char): Int {
        var quote: Char? = null
        var depth = 0
        for (index in openIndex until text.length) {
            val c = text[index]
            if (quote != null) {
                if (c == '\\') continue else if (c == quote) quote = null
            } else {
                when (c) {
                    '\'', '"', '`' -> quote = c
                    open -> depth += 1
                    close -> {
                        depth -= 1
                        if (depth == 0) return index
                    }
                }
            }
        }
        error("Unclosed selector")
    }
}

private class GkdBoolParser(private val source: String) {
    private var index = 0

    fun parse(): GkdBoolExpression {
        val expression = parseOr()
        skipWhitespace()
        if (index != source.length) error("Unexpected token")
        return expression
    }

    private fun parseOr(): GkdBoolExpression {
        var left = parseAnd()
        while (match("||")) left = GkdLogicalBoolExpression("||", left, parseAnd())
        return left
    }

    private fun parseAnd(): GkdBoolExpression {
        var left = parseUnary()
        while (match("&&")) left = GkdLogicalBoolExpression("&&", left, parseUnary())
        return left
    }

    private fun parseUnary(): GkdBoolExpression {
        skipWhitespace()
        if (match("!")) {
            expect("(")
            val inner = parseOr()
            expect(")")
            return GkdNotBoolExpression(inner)
        }
        if (match("(")) {
            val inner = parseOr()
            expect(")")
            return inner
        }
        return parseComparison()
    }

    private fun parseComparison(): GkdBoolExpression {
        val left = parseValue()
        val operator = readOperator()
        val right = operator?.let { parseValue() }
        return GkdComparisonExpression(left, operator, right)
    }

    private fun parseValue(): GkdValueExpression {
        skipWhitespace()
        if (peek() == '(') {
            index += 1
            val inner = parseValue()
            expect(")")
            return inner
        }
        if (peek() == '\'' || peek() == '"' || peek() == '`') return GkdLiteralExpression(GkdValue.StringValue(readString()))
        val number = readNumber()
        if (number != null) return GkdLiteralExpression(GkdValue.IntValue(number))
        val identifier = readIdentifier()
        return when (identifier) {
            "null" -> GkdLiteralExpression(GkdValue.NullValue)
            "true" -> GkdLiteralExpression(GkdValue.BoolValue(true))
            "false" -> GkdLiteralExpression(GkdValue.BoolValue(false))
            else -> parseIdentifierTail(identifier)
        }
    }

    private fun parseIdentifierTail(identifier: String): GkdValueExpression {
        var expression: GkdValueExpression = if (match("(")) {
            GkdCallExpression(null, identifier, readArgumentsAfterOpenParen())
        } else {
            GkdIdentifierExpression(identifier)
        }
        while (match(".")) {
            val member = readIdentifier()
            expression = if (match("(")) {
                GkdCallExpression(expression, member, readArgumentsAfterOpenParen())
            } else {
                GkdMemberExpression(expression, member)
            }
        }
        return expression
    }

    private fun readArgumentsAfterOpenParen(): List<GkdValueExpression> {
        val args = ArrayList<GkdValueExpression>()
        skipWhitespace()
        if (match(")")) return args
        while (true) {
            args.add(parseValue())
            skipWhitespace()
            if (match(")")) return args
            expect(",")
        }
    }

    private fun readOperator(): String? {
        skipWhitespace()
        val operators = listOf("!~=", "!*=", "!^=", "!$=", "==", "!=", ">=", "<=", "^=", "*=", "$=", "~=", "=", ">", "<")
        val operator = operators.firstOrNull { source.startsWith(it, index) } ?: return null
        index += operator.length
        return operator
    }

    private fun readIdentifier(): String {
        skipWhitespace()
        val start = index
        if (index < source.length && (source[index] == '_' || source[index].isLetter())) {
            index += 1
            while (index < source.length && (source[index] == '_' || source[index].isLetterOrDigit())) index += 1
        }
        if (start == index) error("Expected identifier")
        return source.substring(start, index)
    }

    private fun readNumber(): Int? {
        skipWhitespace()
        val start = index
        if (index < source.length && source[index] == '-') index += 1
        while (index < source.length && source[index].isDigit()) index += 1
        if (start == index || (source[start] == '-' && start + 1 == index)) {
            index = start
            return null
        }
        return source.substring(start, index).toIntOrNull()
    }

    private fun readString(): String {
        val quote = source[index]
        index += 1
        val builder = StringBuilder()
        while (index < source.length) {
            val c = source[index]
            index += 1
            if (c == quote) return builder.toString()
            if (c == '\\' && index < source.length) {
                val escaped = source[index]
                index += 1
                builder.append(
                    when (escaped) {
                        'n' -> '\n'
                        'r' -> '\r'
                        't' -> '\t'
                        'b' -> '\b'
                        'x' -> readHexEscape(2)
                        'u' -> readHexEscape(4)
                        else -> escaped
                    }
                )
            } else {
                builder.append(c)
            }
        }
        error("Unclosed string")
    }

    private fun readHexEscape(length: Int): Char {
        if (index + length > source.length) return ' '
        val hex = source.substring(index, index + length)
        val value = hex.toIntOrNull(16) ?: return ' '
        index += length
        return value.toChar()
    }

    private fun match(token: String): Boolean {
        skipWhitespace()
        if (!source.startsWith(token, index)) return false
        index += token.length
        return true
    }

    private fun expect(token: String) {
        if (!match(token)) error("Expected $token")
    }

    private fun peek(): Char? {
        skipWhitespace()
        return source.getOrNull(index)
    }

    private fun skipWhitespace() {
        while (index < source.length && source[index].isWhitespace()) index += 1
    }
}

private fun AccessibilityNodeInfo.bounds(): Rect {
    val rect = Rect()
    getBoundsInScreen(rect)
    return rect
}

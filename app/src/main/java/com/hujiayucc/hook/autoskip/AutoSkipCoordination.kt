package com.hujiayucc.hook.autoskip

internal class LatestPendingGate<T> {
    private val lock = Any()
    private var active = false
    private var pending: T? = null

    fun offer(value: T): T? = synchronized(lock) {
        if (!active) {
            active = true
            value
        } else {
            pending = value
            null
        }
    }

    fun complete(): T? = synchronized(lock) {
        pending.also { next ->
            pending = null
            if (next == null) active = false
        }
    }

    fun clearPending() {
        synchronized(lock) {
            pending = null
        }
    }
}

internal class RuleGenerationCoordinator {
    private val lock = Any()

    fun edit(readGeneration: () -> Long, write: (Long) -> Unit) {
        synchronized(lock) {
            write(nextRuleGeneration(readGeneration()))
        }
    }
}

internal fun nextRuleGeneration(current: Long): Long {
    return if (current == Long.MAX_VALUE) 1L else current + 1L
}

internal class GenerationCache<T : Any> {
    private val lock = Any()
    private var generation: Long? = null
    private var value: T? = null

    fun getOrBuild(currentGeneration: Long, build: (Long) -> T): T = synchronized(lock) {
        val cached = value
        if (generation == currentGeneration && cached != null) return@synchronized cached
        build(currentGeneration).also { built ->
            generation = currentGeneration
            value = built
        }
    }
}

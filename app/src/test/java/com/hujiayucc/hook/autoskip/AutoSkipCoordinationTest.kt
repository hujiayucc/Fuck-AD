package com.hujiayucc.hook.autoskip

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AutoSkipCoordinationTest {
    @Test
    fun gateKeepsOnlyLatestPendingValue() {
        val gate = LatestPendingGate<String>()

        assertEquals("first", gate.offer("first"))
        assertNull(gate.offer("second"))
        assertNull(gate.offer("latest"))
        assertEquals("latest", gate.complete())
        assertNull(gate.complete())
        assertEquals("next", gate.offer("next"))
    }

    @Test
    fun clearingPendingPreventsRerun() {
        val gate = LatestPendingGate<String>()

        assertEquals("active", gate.offer("active"))
        assertNull(gate.offer("pending"))
        gate.clearPending()

        assertNull(gate.complete())
        assertEquals("after-clear", gate.offer("after-clear"))
    }

    @Test
    fun completionAfterSubmissionFailureReleasesGate() {
        val gate = LatestPendingGate<String>()

        assertEquals("active", gate.offer("active"))
        assertNull(gate.offer("pending"))
        assertEquals("pending", gate.complete())
        assertNull(gate.complete())
        assertEquals("retry", gate.offer("retry"))
    }

    @Test
    fun generationWrapsAfterLongMaxValue() {
        assertEquals(1L, nextRuleGeneration(Long.MAX_VALUE))
        assertEquals(42L, nextRuleGeneration(41L))
    }

    @Test
    fun generationCacheReusesAndInvalidatesSnapshots() {
        val cache = GenerationCache<String>()
        var buildCount = 0

        val first = cache.getOrBuild(10L) { generation ->
            buildCount += 1
            "snapshot-$generation-$buildCount"
        }
        val reused = cache.getOrBuild(10L) { error("Same generation must reuse its snapshot") }
        val refreshed = cache.getOrBuild(11L) { generation ->
            buildCount += 1
            "snapshot-$generation-$buildCount"
        }

        assertEquals(first, reused)
        assertEquals("snapshot-11-2", refreshed)
        assertEquals(2, buildCount)
    }

    @Test
    fun coordinatorSerializesConcurrentGenerationUpdates() {
        val coordinator = RuleGenerationCoordinator()
        val threadCount = 8
        val updatesPerThread = 100
        val start = CountDownLatch(1)
        val done = CountDownLatch(threadCount)
        val executor = Executors.newFixedThreadPool(threadCount)
        var generation = 0L

        repeat(threadCount) {
            executor.execute {
                start.await()
                repeat(updatesPerThread) {
                    coordinator.edit(
                        readGeneration = { generation },
                        write = { generation = it }
                    )
                }
                done.countDown()
            }
        }

        start.countDown()
        try {
            check(done.await(10, TimeUnit.SECONDS)) { "Concurrent generation updates timed out" }
            assertEquals((threadCount * updatesPerThread).toLong(), generation)
        } finally {
            executor.shutdownNow()
        }
    }
}
package com.hujiayucc.hook.hooker.util

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HookHandleRegistryTest {
    private val firstMethod = RegistryFixture::class.java.getDeclaredMethod("first")
    private val secondMethod = RegistryFixture::class.java.getDeclaredMethod("second", String::class.java)

    @Test
    fun registryTracksHandlesByOwner() {
        val registry = HookHandleRegistry()

        registry.register("owner-a", firstMethod, Any())
        registry.register("owner-a", secondMethod, Any())
        registry.register("owner-b", firstMethod, Any())

        assertEquals(2, registry.count("owner-a"))
        assertEquals(1, registry.count("owner-b"))
        assertEquals(0, registry.count("missing"))
        assertEquals(
            listOf(firstMethod.toGenericString(), secondMethod.toGenericString()),
            registry.executables("owner-a")
        )
    }

    @Test
    fun clearRemovesOnlyRequestedOwner() {
        val registry = HookHandleRegistry()
        registry.register("owner-a", firstMethod, Any())
        registry.register("owner-a", secondMethod, Any())
        registry.register("owner-b", firstMethod, Any())

        assertEquals(2, registry.clear("owner-a"))
        assertEquals(0, registry.count("owner-a"))
        assertEquals(1, registry.count("owner-b"))
        assertEquals(0, registry.clear("owner-a"))
    }

    @Test
    fun concurrentRegistrationRetainsEveryHandle() {
        val registry = HookHandleRegistry()
        val threadCount = 8
        val handlesPerThread = 100
        val start = CountDownLatch(1)
        val done = CountDownLatch(threadCount)
        val executor = Executors.newFixedThreadPool(threadCount)

        repeat(threadCount) {
            executor.execute {
                start.await()
                repeat(handlesPerThread) {
                    registry.register("shared", firstMethod, Any())
                }
                done.countDown()
            }
        }

        start.countDown()
        try {
            assertTrue(done.await(10, TimeUnit.SECONDS))
            assertEquals(threadCount * handlesPerThread, registry.count("shared"))
            assertEquals(threadCount * handlesPerThread, registry.executables("shared").size)
        } finally {
            executor.shutdownNow()
        }
    }

    private class RegistryFixture {
        fun first() = Unit

        fun second(value: String): String = value
    }
}
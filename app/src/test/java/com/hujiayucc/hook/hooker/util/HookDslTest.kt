package com.hujiayucc.hook.hooker.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class HookDslTest {
    @Test
    fun emptyDslIsInvalid() {
        val dsl = HookDsl()

        assertFalse(dsl.isValid())
        assertEquals("none", dsl.describeCallbacks())
    }

    @Test
    fun replaceDslIsValid() {
        val dsl = HookDsl().apply { replaceTo("blocked") }

        assertTrue(dsl.isValid())
        assertEquals("replace", dsl.describeCallbacks())
    }

    @Test
    fun replaceUnitDslIsValid() {
        val dsl = HookDsl().apply { replaceUnit() }

        assertTrue(dsl.isValid())
        assertEquals("replaceUnit", dsl.describeCallbacks())
    }

    @Test
    fun beforeAndAfterDslIsValid() {
        val dsl = HookDsl().apply {
            before { }
            after { }
        }

        assertTrue(dsl.isValid())
        assertEquals("before, after", dsl.describeCallbacks())
    }

    @Test
    fun replacementCannotBeCombinedWithCallbacks() {
        val dsl = HookDsl().apply {
            replaceTo(null)
            before { }
        }

        assertFalse(dsl.isValid())
        assertEquals("replace, before", dsl.describeCallbacks())
    }

    @Test
    fun replaceAndReplaceUnitCannotBeCombined() {
        val dsl = HookDsl().apply {
            replaceTo("value")
            replaceUnit()
        }

        assertFalse(dsl.isValid())
        assertEquals("replace, replaceUnit", dsl.describeCallbacks())
    }

    @Test
    fun duplicateReplaceReportsConflictAndKeepsLatestBlock() {
        val duplicates = mutableListOf<String>()
        val firstValue = Any()
        val latestValue = Any()
        val dsl = HookDsl(duplicates::add).apply {
            replaceTo(firstValue)
            replaceTo(latestValue)
        }

        assertEquals(listOf("replace/replaceTo"), duplicates)
        assertSame(latestValue, dsl.replaceBlock?.invoke(callbackStub()))
    }

    @Test
    fun duplicateLifecycleCallbackReportsConflictAndKeepsLatestBlock() {
        val duplicates = mutableListOf<String>()
        var invocation = "none"
        val dsl = HookDsl(duplicates::add).apply {
            before { invocation = "first" }
            before { invocation = "latest" }
        }

        dsl.beforeBlock?.invoke(callbackStub())

        assertEquals(listOf("before"), duplicates)
        assertEquals("latest", invocation)
        assertNull(dsl.afterBlock)
    }

    private fun callbackStub(): HookCallback {
        return object : HookCallback {
            override val chain get() = error("Chain is not used by DSL state tests")
            override val thisObject: Any? = null
            override val args: List<Any?> = emptyList()
            override val hasResult: Boolean = false
            override var result: Any? = null

            override fun proceed(): Any? = null

            override fun proceedWith(args: Array<Any?>): Any? = null

            override fun proceedWith(thisObject: Any?, args: Array<Any?>): Any? = null
        }
    }
}
package com.hujiayucc.hook.hooker.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HookInvocationStateTest {
    @Test
    fun unsetAndExplicitNullResultsAreDistinguished() {
        val state = HookInvocationState()

        assertFalse(state.hasResult)
        assertNull(state.result)

        state.result = null

        assertTrue(state.hasResult)
        assertNull(state.result)
    }

    @Test
    fun originalCanStartOnlyOnce() {
        val state = HookInvocationState()

        assertTrue(state.startOriginal())
        assertTrue(state.originalExecuted)
        assertFalse(state.startOriginal())
    }

    @Test
    fun currentResultRemainsAvailableAfterRepeatedStart() {
        val state = HookInvocationState()
        state.result = "before"

        assertTrue(state.startOriginal())
        state.result = "original"
        assertFalse(state.startOriginal())

        assertEquals("original", state.result)
    }
}
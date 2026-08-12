package com.hujiayucc.hook.autoskip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AutoSkipClickAttemptFlowTest {
    @Test
    fun synchronousRejectionContinuesToNextAttempt() {
        val executed = mutableListOf<String>()
        val verifications = ArrayDeque(
            listOf(
                AutoSkipClickVerification(false, "still visible"),
                AutoSkipClickVerification(true, "disappeared")
            )
        )

        val outcome = runAutoSkipAttemptFlow(
            attempts = listOf("disabled", "failed", "first", "second"),
            startIndex = 0,
            isEnabled = { it != "disabled" },
            execute = { attempt ->
                executed += attempt
                attempt != "failed"
            },
            verifier = { verifications.removeFirst() },
            deferVerification = false
        )

        assertEquals(listOf("failed", "first", "second"), executed)
        assertEquals("second", outcome.acceptedAttempt)
        assertEquals("disappeared", outcome.verification?.message)
        assertNull(outcome.lastRejectedAttempt)
    }

    @Test
    fun deferredVerificationRetriesFromFollowingAttempt() {
        val outcome = runAutoSkipAttemptFlow(
            attempts = listOf("first", "second", "third"),
            startIndex = 1,
            isEnabled = { true },
            execute = { true },
            verifier = { error("Deferred verification must not run inline") },
            deferVerification = true
        )

        assertEquals("second", outcome.acceptedAttempt)
        assertEquals(2, outcome.retryStartIndex)
        assertNull(outcome.verification)
    }

    @Test
    fun finalRejectedAttemptIsPreservedWhenNoAttemptIsAccepted() {
        val outcome = runAutoSkipAttemptFlow(
            attempts = listOf("first", "second"),
            startIndex = 0,
            isEnabled = { true },
            execute = { true },
            verifier = { AutoSkipClickVerification(false, "still visible") },
            deferVerification = false
        )

        assertNull(outcome.acceptedAttempt)
        assertEquals("second", outcome.lastRejectedAttempt)
        assertEquals("still visible", outcome.lastRejection?.message)
    }
}

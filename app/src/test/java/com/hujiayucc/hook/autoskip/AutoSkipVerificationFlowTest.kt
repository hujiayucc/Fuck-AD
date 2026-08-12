package com.hujiayucc.hook.autoskip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoSkipVerificationFlowTest {
    private val original = AutoSkipExecutionResult(true, "gesture", null, VERIFICATION_SCHEDULED_MESSAGE)

    @Test
    fun verificationFailureIsAcceptedAndRecorded() {
        val outcome = runAutoSkipVerificationFlow(
            originalResult = original,
            verifier = { error("boom") },
            retry = { error("Accepted verification must not retry") }
        )

        assertEquals("ok; verification failed: IllegalStateException", outcome.resultToRecord?.message)
        assertFalse(outcome.markCooldown)
    }

    @Test
    fun rejectedVerificationWithoutRetryRecordsOriginalAttempt() {
        val outcome = runAutoSkipVerificationFlow(
            originalResult = original,
            verifier = { AutoSkipClickVerification(false, "target still visible") },
            retry = null
        )

        assertEquals("gesture", outcome.resultToRecord?.executor)
        assertEquals("target still visible", outcome.resultToRecord?.message)
        assertFalse(outcome.markCooldown)
    }

    @Test
    fun successfulScheduledRetryMarksCooldownWithoutDuplicateRecord() {
        val outcome = runAutoSkipVerificationFlow(
            originalResult = original,
            verifier = { AutoSkipClickVerification(false, "target still visible") },
            retry = {
                AutoSkipExecutionResult(true, "root", null, VERIFICATION_SCHEDULED_MESSAGE)
            }
        )

        assertTrue(outcome.markCooldown)
        assertNull(outcome.resultToRecord)
    }

    @Test
    fun failedRetryIsRecordedWithoutCooldown() {
        val retryResult = AutoSkipExecutionResult(false, "none", null, "All executors failed")
        val outcome = runAutoSkipVerificationFlow(
            originalResult = original,
            verifier = { AutoSkipClickVerification(false, "target still visible") },
            retry = { retryResult }
        )

        assertFalse(outcome.markCooldown)
        assertEquals(retryResult, outcome.resultToRecord)
    }
}
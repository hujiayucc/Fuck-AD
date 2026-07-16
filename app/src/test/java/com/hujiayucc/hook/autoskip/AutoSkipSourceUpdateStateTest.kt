package com.hujiayucc.hook.autoskip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoSkipSourceUpdateStateTest {
    @Test
    fun emptyAndAbnormallyReducedDownloadsAreRejected() {
        assertEquals(DownloadedRuleRejection.EMPTY, downloadedRuleRejection(0, 0))
        assertEquals(DownloadedRuleRejection.ABNORMAL_DECREASE, downloadedRuleRejection(12, 3))
        assertNull(downloadedRuleRejection(12, 4))
        assertNull(downloadedRuleRejection(9, 1))
    }

    @Test
    fun failedSourcesDoNotPermitRulePersistence() {
        val state = SourceUpdateState(emptyMap())

        state.recordFailure()
        state.recordFailure()

        assertEquals(2, state.failed)
        assertEquals(0, state.successful)
        assertFalse(state.shouldPersistRules)
    }

    @Test
    fun anySuccessfulSourcePermitsPersistenceAndAccumulatesDiffs() {
        val state = SourceUpdateState(emptyMap())

        state.recordFailure()
        state.recordDiff(RuleDiff(added = 3, changed = 2, removed = 1))
        state.recordSuccess()
        state.recordDiff(RuleDiff(added = 4, changed = 0, removed = 2))
        state.recordSuccess()

        assertEquals(1, state.failed)
        assertEquals(2, state.successful)
        assertEquals(7, state.added)
        assertEquals(2, state.changed)
        assertEquals(3, state.removed)
        assertTrue(state.shouldPersistRules)
    }
}
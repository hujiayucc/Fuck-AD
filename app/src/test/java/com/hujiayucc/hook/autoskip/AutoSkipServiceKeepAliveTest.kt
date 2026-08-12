package com.hujiayucc.hook.autoskip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoSkipServiceKeepAliveTest {
    @Test
    fun keepAliveRequiresSettingAndUnrestrictedBatteryUsage() {
        assertFalse(serviceKeepAliveCondition(configured = false, unrestricted = false))
        assertFalse(serviceKeepAliveCondition(configured = false, unrestricted = true))
        assertFalse(serviceKeepAliveCondition(configured = true, unrestricted = false))
        assertTrue(serviceKeepAliveCondition(configured = true, unrestricted = true))
    }

    @Test
    fun heartbeatRunsOnlyWhenAKeepAliveMechanismNeedsIt() {
        assertFalse(keepAliveHeartbeatRequired(serviceKeepAliveEnabled = false, daemonKeepAliveEnabled = false))
        assertTrue(keepAliveHeartbeatRequired(serviceKeepAliveEnabled = true, daemonKeepAliveEnabled = false))
        assertTrue(keepAliveHeartbeatRequired(serviceKeepAliveEnabled = false, daemonKeepAliveEnabled = true))
        assertTrue(keepAliveHeartbeatRequired(serviceKeepAliveEnabled = true, daemonKeepAliveEnabled = true))
    }

    @Test
    fun serviceHeartbeatUsesReducedFrequencyWithoutDaemon() {
        assertEquals(0L, heartbeatIntervalMs(serviceKeepAliveEnabled = false, daemonKeepAliveEnabled = false))
        assertEquals(60_000L, heartbeatIntervalMs(serviceKeepAliveEnabled = true, daemonKeepAliveEnabled = false))
        assertEquals(10_000L, heartbeatIntervalMs(serviceKeepAliveEnabled = false, daemonKeepAliveEnabled = true))
        assertEquals(10_000L, heartbeatIntervalMs(serviceKeepAliveEnabled = true, daemonKeepAliveEnabled = true))
        assertEquals(60_000L, healthWriteIntervalMs(serviceKeepAliveEnabled = true, daemonKeepAliveEnabled = false))
        assertEquals(10_000L, healthWriteIntervalMs(serviceKeepAliveEnabled = false, daemonKeepAliveEnabled = true))
    }

    @Test
    fun staleOrDisconnectedHeartbeatIsNotReportedAsActive() {
        assertFalse(isServiceHeartbeatFresh(serviceConnected = false, lastHeartbeatAt = 99_000L, now = 100_000L, freshWindowMs = 120_000L))
        assertFalse(isServiceHeartbeatFresh(serviceConnected = true, lastHeartbeatAt = 0L, now = 100_000L, freshWindowMs = 120_000L))
        assertTrue(isServiceHeartbeatFresh(serviceConnected = true, lastHeartbeatAt = 99_000L, now = 100_000L, freshWindowMs = 120_000L))
        assertFalse(isServiceHeartbeatFresh(serviceConnected = true, lastHeartbeatAt = 100_000L, now = 221_000L, freshWindowMs = 120_000L))
    }

    @Test
    fun healthStateUsesConnectionFlagAndFreshHeartbeat() {
        val connected = AutoSkipHealthState(serviceConnected = true, lastHeartbeatAt = 99_000L)
        val disconnected = connected.copy(serviceConnected = false)

        assertTrue(AutoSkipHealth.hasFreshHeartbeat(connected, now = 100_000L, freshWindowMs = 120_000L))
        assertFalse(AutoSkipHealth.hasFreshHeartbeat(disconnected, now = 100_000L, freshWindowMs = 120_000L))
        assertFalse(AutoSkipHealth.hasFreshHeartbeat(connected, now = 221_000L, freshWindowMs = 120_000L))
    }

    @Test
    fun cooldownCachesDiscardExpiredEntriesAndStayBounded() {
        val appEntries = hashMapOf(
            "com.example.active" to 99_000L,
            "com.example.expired" to 96_000L,
            "com.example.future" to 101_000L
        )
        removeExpiredCooldownEntries(appEntries, now = 100_000L, durationMs = 3_000L)

        assertEquals(setOf("com.example.active"), appEntries.keys)

        val ruleEntries = hashMapOf(
            "old" to 1L,
            "middle" to 2L,
            "new" to 3L
        )
        boundCooldownEntries(ruleEntries, maxEntries = 2)

        assertEquals(setOf("middle", "new"), ruleEntries.keys)
    }

    @Test
    fun foregroundDecisionRequiresKeepAliveAndReadyEngine() {
        assertTrue(shouldRunAccessibilityForeground(serviceKeepAliveEnabled = true, engineReady = true))
        assertFalse(shouldRunAccessibilityForeground(serviceKeepAliveEnabled = false, engineReady = true))
        assertFalse(shouldRunAccessibilityForeground(serviceKeepAliveEnabled = true, engineReady = false))
    }

    @Test
    fun healthErrorClearsOnlyAfterContinuousHealthyPeriod() {
        assertFalse(shouldClearHealthError(healthySinceAt = 0L, now = 60_000L, clearDelayMs = 60_000L))
        assertFalse(shouldClearHealthError(healthySinceAt = 1_000L, now = 60_999L, clearDelayMs = 60_000L))
        assertTrue(shouldClearHealthError(healthySinceAt = 1_000L, now = 61_000L, clearDelayMs = 60_000L))
    }
}

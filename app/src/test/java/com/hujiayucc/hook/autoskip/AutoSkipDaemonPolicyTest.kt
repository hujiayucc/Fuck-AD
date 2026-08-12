package com.hujiayucc.hook.autoskip

import com.hujiayucc.hook.utils.accessibilityComponentsEqual
import com.hujiayucc.hook.utils.shouldRollbackAccessibilityEnabled
import com.hujiayucc.hook.utils.shouldRollbackAccessibilityServices
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoSkipDaemonPolicyTest {
    @Test
    fun autoRepairRequiresEnabledDaemonAndStaleStatus() {
        val now = 1_000_000L

        assertFalse(
            shouldAutoRepairDaemon(
                daemonEnabled = false,
                statusLastCheckAt = null,
                lastRepairAttemptAt = 0L,
                now = now
            )
        )
        assertFalse(
            shouldAutoRepairDaemon(
                daemonEnabled = true,
                statusLastCheckAt = now - 90_000L,
                lastRepairAttemptAt = 0L,
                now = now,
                statusSchemaVersion = AutoSkipDaemonManager.CURRENT_STATUS_SCHEMA_VERSION
            )
        )
        assertTrue(
            shouldAutoRepairDaemon(
                daemonEnabled = true,
                statusLastCheckAt = now - 90_001L,
                lastRepairAttemptAt = 0L,
                now = now,
                statusSchemaVersion = AutoSkipDaemonManager.CURRENT_STATUS_SCHEMA_VERSION
            )
        )
    }

    @Test
    fun autoRepairUpdatesFreshOlderDaemonSchema() {
        val now = 1_500_000L

        assertTrue(
            shouldAutoRepairDaemon(
                daemonEnabled = true,
                statusLastCheckAt = now,
                lastRepairAttemptAt = 0L,
                now = now,
                statusSchemaVersion = AutoSkipDaemonManager.CURRENT_STATUS_SCHEMA_VERSION - 1
            )
        )
        assertFalse(
            shouldAutoRepairDaemon(
                daemonEnabled = true,
                statusLastCheckAt = now,
                lastRepairAttemptAt = 0L,
                now = now,
                statusSchemaVersion = AutoSkipDaemonManager.CURRENT_STATUS_SCHEMA_VERSION
            )
        )
        assertFalse(
            shouldAutoRepairDaemon(
                daemonEnabled = true,
                statusLastCheckAt = now,
                lastRepairAttemptAt = now - 1L,
                now = now,
                statusSchemaVersion = AutoSkipDaemonManager.CURRENT_STATUS_SCHEMA_VERSION - 1
            )
        )
    }

    @Test
    fun autoRepairCooldownSurvivesActivityRecreation() {
        val now = 2_000_000L

        assertFalse(
            shouldAutoRepairDaemon(
                daemonEnabled = true,
                statusLastCheckAt = null,
                lastRepairAttemptAt = now - 899_999L,
                now = now
            )
        )
        assertTrue(
            shouldAutoRepairDaemon(
                daemonEnabled = true,
                statusLastCheckAt = null,
                lastRepairAttemptAt = now - 900_000L,
                now = now
            )
        )
    }

    @Test
    fun clockRollbackDoesNotBlockRepairIndefinitely() {
        assertTrue(
            shouldAutoRepairDaemon(
                daemonEnabled = true,
                statusLastCheckAt = 2_000_000L,
                lastRepairAttemptAt = 2_000_000L,
                now = 1_000_000L
            )
        )
    }

    @Test
    fun accessibilityComponentAliasesAreEquivalent() {
        val full = "com.hujiayucc.hook/com.hujiayucc.hook.autoskip.AutoSkipAccessibilityService"
        val short = "com.hujiayucc.hook/.autoskip.AutoSkipAccessibilityService"

        assertTrue(accessibilityComponentsEqual(full, short))
        assertTrue(accessibilityComponentsEqual(short, full.uppercase()))
        assertFalse(accessibilityComponentsEqual(short, "com.other/.autoskip.AutoSkipAccessibilityService"))
    }

    @Test
    fun conditionalRollbackPreservesExternalSettingsUpdates() {
        val writtenValues = setOf("service.one:target", "service.one")

        assertTrue(shouldRollbackAccessibilityEnabled(1))
        assertFalse(shouldRollbackAccessibilityEnabled(0))
        assertFalse(shouldRollbackAccessibilityEnabled(null))
        assertTrue(shouldRollbackAccessibilityServices("service.one", writtenValues))
        assertFalse(shouldRollbackAccessibilityServices("service.one:external", writtenValues))
        assertFalse(shouldRollbackAccessibilityServices(null, writtenValues))
    }

    @Test
    fun accessibilitySettingsLeaseSerializesAndReleases() {
        val root = Files.createTempDirectory("accessibility-settings-lock").toFile()
        try {
            val now = { 100_000L }
            val first = AccessibilitySettingsLease.acquire(
                directory = File(root, AccessibilitySettingsLease.LOCK_DIRECTORY_NAME),
                operation = "client_accessibility_enable",
                nowMillis = now,
                sleepMillis = {},
                timeoutMs = 0L
            )
            assertNotNull(first)

            val second = AccessibilitySettingsLease.acquire(
                directory = File(root, AccessibilitySettingsLease.LOCK_DIRECTORY_NAME),
                operation = "daemon_rebind",
                nowMillis = now,
                sleepMillis = {},
                timeoutMs = 0L
            )
            assertNull(second)
            assertEquals("client", File(root, "${AccessibilitySettingsLease.LOCK_DIRECTORY_NAME}/owner").readText())

            first?.close()
            val third = AccessibilitySettingsLease.acquire(
                directory = File(root, AccessibilitySettingsLease.LOCK_DIRECTORY_NAME),
                operation = "daemon_rebind",
                nowMillis = now,
                sleepMillis = {},
                timeoutMs = 0L
            )
            assertNotNull(third)
            third?.close()
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun expiredAccessibilitySettingsLeaseCanBeReclaimed() {
        val root = Files.createTempDirectory("accessibility-settings-stale-lock").toFile()
        try {
            val lockDirectory = File(root, AccessibilitySettingsLease.LOCK_DIRECTORY_NAME)
            assertTrue(lockDirectory.mkdir())
            File(lockDirectory, "owner").writeText("daemon")
            File(lockDirectory, "token").writeText("old-token")
            File(lockDirectory, "leaseUntilSeconds").writeText("10")

            val lease = AccessibilitySettingsLease.acquire(
                directory = lockDirectory,
                operation = "client_accessibility_enable",
                nowMillis = { 20_000L },
                sleepMillis = {},
                timeoutMs = 0L
            )
            assertNotNull(lease)
            assertEquals("client", File(lockDirectory, "owner").readText())
            lease?.close()
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun clockRollbackDoesNotLeaveSettingsLeaseActiveIndefinitely() {
        val root = Files.createTempDirectory("accessibility-settings-future-lock").toFile()
        try {
            val lockDirectory = File(root, AccessibilitySettingsLease.LOCK_DIRECTORY_NAME)
            assertTrue(lockDirectory.mkdir())
            File(lockDirectory, "owner").writeText("daemon")
            File(lockDirectory, "token").writeText("future-token")
            File(lockDirectory, "leaseUntilSeconds").writeText("10000")

            val lease = AccessibilitySettingsLease.acquire(
                directory = lockDirectory,
                operation = "client_accessibility_enable",
                nowMillis = { 20_000L },
                sleepMillis = {},
                timeoutMs = 0L
            )
            assertNotNull(lease)
            assertEquals("client", File(lockDirectory, "owner").readText())
            lease?.close()
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun packageStopRestoreRequiresAbruptFreshServiceState() {
        assertTrue(runPackageStopRestoreCandidate())
        assertTrue(runPackageStopRestoreCandidate(heartbeatAt = 999_999_910_000L))
        assertFalse(runPackageStopRestoreCandidate(heartbeatAt = 999_999_909_000L))
        assertFalse(runPackageStopRestoreCandidate(accessibilityEnabled = false))
        assertFalse(runPackageStopRestoreCandidate(packageStopped = false))
        assertFalse(runPackageStopRestoreCandidate(healthConnected = false))
        assertFalse(runPackageStopRestoreCandidate(heartbeatAt = 1_000L))
        assertFalse(runPackageStopRestoreCandidate(lastConnectedAt = 0L))
    }

    @Test
    fun packageStopRestoreUsesTemporaryServiceStartToClearStoppedState() {
        val success = runWatchdogFunction(
            overrides = """
                fake_stopped=true
                start_calls=0
                stop_calls=0
                package_stopped() { [ "${'$'}fake_stopped" = "true" ]; }
                am() {
                    case "${'$'}1" in
                        startservice)
                            start_calls=${'$'}((start_calls + 1))
                            fake_stopped=false
                            ;;
                        stopservice)
                            stop_calls=${'$'}((stop_calls + 1))
                            ;;
                        *) return 1 ;;
                    esac
                    return 0
                }
                sleep() { :; }
            """,
            invocation = "clear_package_stopped_state && [ \"\$start_calls\" -eq 1 ] && [ \"\$stop_calls\" -eq 1 ]"
        )
        assertEquals(success.output, 0, success.exitCode)

        val failure = runWatchdogFunction(
            overrides = """
                package_stopped() { return 0; }
                am() { return 1; }
                sleep() { :; }
            """,
            invocation = "clear_package_stopped_state"
        )
        assertTrue(failure.exitCode != 0)
    }

    @Test
    fun packageStopRestoreIsWiredThroughExistingRecoveryGuards() {
        val script = watchdogScriptFile().readText()
        assertTrue(script.contains("STATUS_SCHEMA_VERSION=${AutoSkipDaemonManager.CURRENT_STATUS_SCHEMA_VERSION}"))
        assertTrue(script.contains("if package_stop_restore_candidate; then"))
        assertTrue(script.contains("settings_lock_acquire \"daemon\" \"\$settings_operation\""))
        assertTrue(script.contains("restore_service_after_package_stop"))
        assertTrue(script.contains("wait_for_service_rebind \"\$attempt_started_at\""))
        assertTrue(script.contains("package_stop_restore_verified"))
    }

    private fun runPackageStopRestoreCandidate(
        accessibilityEnabled: Boolean = true,
        packageStopped: Boolean = true,
        healthConnected: Boolean = true,
        heartbeatAt: Long = 1_000_000_000_000L,
        lastConnectedAt: Long = 999_999_999_000L
    ): Boolean {
        val result = runWatchdogFunction(
            overrides = """
                HEALTH_FILE="${'$'}0.health"
                : > "${'$'}HEALTH_FILE"
                trap 'rm -f "${'$'}HEALTH_FILE"' EXIT
                read_accessibility_enabled() { echo ${if (accessibilityEnabled) 1 else 0}; }
                package_stopped() { return ${if (packageStopped) 0 else 1}; }
                json_bool() { return ${if (healthConnected) 0 else 1}; }
                last_heartbeat() { echo $heartbeatAt; }
                last_connected_at() { echo $lastConnectedAt; }
                now_sec() { echo 1000000000; }
            """,
            invocation = "package_stop_restore_candidate"
        )
        return result.exitCode == 0
    }

    private fun runWatchdogFunction(overrides: String, invocation: String): ShellResult {
        val source = watchdogScriptFile().readText()
        val entryPoint = "\ncase \"\$1\" in"
        assertTrue("watchdog entry point not found", source.contains(entryPoint))
        val harness = Files.createTempFile("autoskip-watchdog-policy", ".sh").toFile()
        return try {
            harness.writeText(
                source.substringBefore(entryPoint) +
                    "\n" + overrides.trimIndent() + "\n" + invocation + "\n"
            )
            val process = ProcessBuilder("/bin/sh", harness.absolutePath)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            ShellResult(process.waitFor(), output)
        } finally {
            harness.delete()
        }
    }

    private fun watchdogScriptFile(): File {
        return sequenceOf(
            File("src/main/res/raw/autoskip_watchdog.sh"),
            File("app/src/main/res/raw/autoskip_watchdog.sh")
        ).firstOrNull(File::isFile) ?: error("autoskip_watchdog.sh not found")
    }
    private data class ShellResult(val exitCode: Int, val output: String)
}

package com.hujiayucc.hook.autoskip

import android.content.Context
import java.io.Closeable
import java.io.File
import java.util.UUID

/**
 * Cross-process lease for transactions that modify accessibility Secure Settings.
 *
 * The watchdog shell and the app deliberately use the same directory and metadata
 * file names. mkdir() is the ownership primitive; the lease makes a crashed owner
 * recoverable without relying on a process-local JVM lock.
 */
internal object AccessibilitySettingsLease {
    const val LOCK_DIRECTORY_NAME = "autoskip_accessibility_settings.lock"
    const val LEASE_SECONDS = 90L
    const val ACQUIRE_TIMEOUT_MS = 10_000L
    const val POLL_MS = 100L

    private const val OWNER_FILE_NAME = "owner"
    private const val OPERATION_FILE_NAME = "operation"
    private const val TOKEN_FILE_NAME = "token"
    private const val LEASE_UNTIL_FILE_NAME = "leaseUntilSeconds"
    private const val STARTED_AT_FILE_NAME = "startedAtMillis"
    private const val INITIALIZATION_GRACE_MS = 5_000L

    fun lockDirectory(context: Context): File {
        return File(context.applicationContext.noBackupFilesDir, LOCK_DIRECTORY_NAME)
    }

    fun acquire(context: Context, operation: String): Lease? {
        return acquire(lockDirectory(context), operation)
    }

    internal fun acquire(
        directory: File,
        operation: String,
        nowMillis: () -> Long = { System.currentTimeMillis() },
        sleepMillis: (Long) -> Unit = ::sleepQuietly,
        timeoutMs: Long = ACQUIRE_TIMEOUT_MS
    ): Lease? {
        directory.parentFile?.mkdirs()
        val deadline = nowMillis() + timeoutMs.coerceAtLeast(0L)
        while (nowMillis() <= deadline) {
            if (directory.mkdir()) {
                val token = UUID.randomUUID().toString()
                val startedAtMillis = nowMillis()
                val leaseUntilSeconds = startedAtMillis / 1_000L + LEASE_SECONDS
                val lease = Lease(directory, token, leaseUntilSeconds)
                if (lease.writeMetadata(operation, startedAtMillis)) return lease
                directory.deleteRecursively()
                return null
            }
            reclaimIfExpired(directory, nowMillis())
            if (!directory.exists()) continue
            if (timeoutMs <= 0L) return null
            sleepMillis(POLL_MS)
        }
        return null
    }

    private fun reclaimIfExpired(directory: File, nowMillis: Long) {
        if (!directory.isDirectory) return
        val leaseFile = File(directory, LEASE_UNTIL_FILE_NAME)
        val tokenFile = File(directory, TOKEN_FILE_NAME)
        val observedLease = readLong(leaseFile)
        val nowSeconds = nowMillis / 1_000L
        if (observedLease != null &&
            observedLease > nowSeconds &&
            observedLease - nowSeconds <= LEASE_SECONDS * 2
        ) {
            return
        }
        if (observedLease == null) {
            val modifiedAt = directory.lastModified()
            if (modifiedAt <= 0L || nowMillis - modifiedAt < INITIALIZATION_GRACE_MS) return
        }

        val observedToken = readText(tokenFile)
        val currentLease = readLong(leaseFile)
        val currentToken = readText(tokenFile)
        if (currentLease == observedLease && currentToken == observedToken) {
            directory.deleteRecursively()
        }
    }

    private fun readText(file: File): String? {
        return runCatching { if (file.isFile) file.readText().trim() else null }.getOrNull()
    }

    private fun readLong(file: File): Long? {
        return readText(file)?.toLongOrNull()?.takeIf { it >= 0L }
    }

    private fun sleepQuietly(durationMs: Long) {
        try {
            Thread.sleep(durationMs)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    internal class Lease internal constructor(
        private val directory: File,
        private val token: String,
        private var leaseUntilSeconds: Long
    ) : Closeable {
        val owner: String = "client"
        val tokenValue: String get() = token
        val leaseUntil: Long get() = leaseUntilSeconds

        internal fun renew(nowMillis: Long = System.currentTimeMillis()): Boolean {
            if (!isOwner()) return false
            leaseUntilSeconds = nowMillis / 1_000L + LEASE_SECONDS
            return runCatching {
                File(directory, LEASE_UNTIL_FILE_NAME).writeText(leaseUntilSeconds.toString())
                true
            }.getOrDefault(false)
        }

        override fun close() {
            if (!isOwner()) return
            runCatching {
                File(directory, OWNER_FILE_NAME).delete()
                File(directory, OPERATION_FILE_NAME).delete()
                File(directory, TOKEN_FILE_NAME).delete()
                File(directory, LEASE_UNTIL_FILE_NAME).delete()
                File(directory, STARTED_AT_FILE_NAME).delete()
                directory.delete()
            }
        }

        private fun isOwner(): Boolean {
            return readText(File(directory, TOKEN_FILE_NAME)) == token
        }

        internal fun writeMetadata(operation: String, startedAtMillis: Long): Boolean {
            return runCatching {
                File(directory, OWNER_FILE_NAME).writeText(owner)
                File(directory, OPERATION_FILE_NAME).writeText(operation)
                File(directory, TOKEN_FILE_NAME).writeText(token)
                File(directory, LEASE_UNTIL_FILE_NAME).writeText(leaseUntilSeconds.toString())
                File(directory, STARTED_AT_FILE_NAME).writeText(startedAtMillis.toString())
                directory.setLastModified(startedAtMillis)
                true
            }.getOrDefault(false)
        }
    }
}

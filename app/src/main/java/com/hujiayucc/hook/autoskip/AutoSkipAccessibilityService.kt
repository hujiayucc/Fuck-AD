package com.hujiayucc.hook.autoskip

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.hujiayucc.hook.R
import com.hujiayucc.hook.ui.activity.AutoSkipRulesActivity
import com.hujiayucc.hook.utils.LanguageUtils
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

private const val DAEMON_HEARTBEAT_INTERVAL_MS = 10_000L
private const val SERVICE_HEARTBEAT_INTERVAL_MS = 60_000L
private const val NOTIFICATION_REFRESH_INTERVAL_MS = 60_000L
private const val NOTIFICATION_STALE_TIMEOUT_MS = 130_000L
private const val HEALTHY_ERROR_CLEAR_DELAY_MS = 60_000L

class AutoSkipAccessibilityService : AccessibilityService() {
    private lateinit var engine: AutoSkipEngine
    private val heartbeatExecutor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "AutoSkipKeepAlive").apply { isDaemon = true }
    }
    private var notificationPackageName: String? = null
    private var heartbeatFuture: ScheduledFuture<*>? = null
    private var scheduledHeartbeatIntervalMs = 0L
    private var engineGeneration = 0
    private var consecutiveEventFailures = 0
    @Volatile
    private var lastHealthWriteAt = 0L
    @Volatile
    private var lastNotificationPostAt = 0L
    @Volatile
    private var healthySinceAt = 0L
    @Volatile
    private var healthErrorCleared = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        current = this
        ensureServiceInfo()
        recreateEngine("connected")
        val now = System.currentTimeMillis()
        healthySinceAt = now
        lastHealthWriteAt = now
        AutoSkipHealth.markConnected(this, engineGeneration)
        AutoSkipDaemonManager.writeConfig(this, preserveExistingEnabled = true)
        refreshKeepAlive()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (!::engine.isInitialized) recreateEngine("event")
        val packageName = notificationPackageNameFromEvent(event)
        val canUpdateTitle = isNotificationTitleEvent(event, packageName)
        runCatching {
            markNotificationEventIfNeeded(event, if (canUpdateTitle) packageName else "")
            if (canUpdateTitle) {
                updateNotificationForPackage(packageName)
            }
            engine.onAccessibilityEvent(event)
        }.onSuccess {
            consecutiveEventFailures = 0
            markHealthy()
        }.onFailure { error ->
            handleServiceError("event", error)
        }
    }

    override fun onInterrupt() {
        val now = System.currentTimeMillis()
        if (now - lastHealthWriteAt < SERVICE_HEARTBEAT_INTERVAL_MS) return
        lastHealthWriteAt = now
        AutoSkipHealth.markHeartbeat(this, engineGeneration)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        AutoSkipHealth.markDisconnected(this, "unbind")
        stopHeartbeat()
        cancelRunningNotification(this)
        if (current === this) current = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        AutoSkipHealth.markDisconnected(this, "destroy")
        if (current === this) current = null
        runCatching { NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID) }
        runCatching { if (::engine.isInitialized) engine.shutdown() }
        heartbeatExecutor.shutdownNow()
        super.onDestroy()
    }

    private fun refreshKeepAlive() {
        val serviceKeepAliveEnabled = AutoSkipSettings.serviceKeepAliveEnabled(this)
        val daemonKeepAliveEnabled = AutoSkipSettings.daemonKeepAliveEnabled(this)
        if (serviceKeepAliveEnabled) {
            if (!::engine.isInitialized) recreateEngine("keep_alive")
            showRunningNotification()
        } else {
            cancelRunningNotification(this)
        }
        if (keepAliveHeartbeatRequired(serviceKeepAliveEnabled, daemonKeepAliveEnabled)) {
            startHeartbeat(heartbeatIntervalMs(serviceKeepAliveEnabled, daemonKeepAliveEnabled))
        } else {
            stopHeartbeat()
        }
    }

    @Synchronized
    private fun startHeartbeat(intervalMs: Long) {
        val activeFuture = heartbeatFuture
        if (activeFuture != null && !activeFuture.isCancelled && !activeFuture.isDone &&
            scheduledHeartbeatIntervalMs == intervalMs
        ) {
            return
        }
        activeFuture?.cancel(false)
        scheduledHeartbeatIntervalMs = intervalMs
        heartbeatFuture = heartbeatExecutor.scheduleWithFixedDelay(
            {
                runCatching { keepAliveTick() }
                    .onFailure { error -> recordKeepAliveError("heartbeat", error) }
            },
            intervalMs,
            intervalMs,
            TimeUnit.MILLISECONDS
        )
    }

    @Synchronized
    private fun stopHeartbeat() {
        heartbeatFuture?.cancel(false)
        heartbeatFuture = null
        scheduledHeartbeatIntervalMs = 0L
    }

    private fun keepAliveTick() {
        val serviceKeepAliveEnabled = AutoSkipSettings.serviceKeepAliveEnabled(this)
        val daemonKeepAliveEnabled = AutoSkipSettings.daemonKeepAliveEnabled(this)
        if (!serviceKeepAliveEnabled) {
            cancelRunningNotification(this)
        }
        if (!keepAliveHeartbeatRequired(serviceKeepAliveEnabled, daemonKeepAliveEnabled)) {
            stopHeartbeat()
            return
        }
        val now = System.currentTimeMillis()
        val writeIntervalMs = healthWriteIntervalMs(serviceKeepAliveEnabled, daemonKeepAliveEnabled)
        if (now - lastHealthWriteAt >= writeIntervalMs) {
            lastHealthWriteAt = now
            AutoSkipHealth.markHeartbeat(this, engineGeneration)
        }
        if (serviceKeepAliveEnabled && now - lastNotificationPostAt >= NOTIFICATION_REFRESH_INTERVAL_MS) {
            showRunningNotification()
        }
        if (serviceKeepAliveEnabled && !::engine.isInitialized) recreateEngine("heartbeat")
        startHeartbeat(heartbeatIntervalMs(serviceKeepAliveEnabled, daemonKeepAliveEnabled))
        markHealthy(now)
    }

    private fun ensureServiceInfo() {
        val info = serviceInfo ?: return
        val requiredEvents = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
            AccessibilityEvent.TYPE_WINDOWS_CHANGED or
            AccessibilityEvent.TYPE_VIEW_FOCUSED
        info.eventTypes = info.eventTypes or requiredEvents
        info.flags = info.flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
            AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        serviceInfo = info
    }

    @Synchronized
    private fun recreateEngine(reason: String) {
        runCatching { if (::engine.isInitialized) engine.shutdown() }
        engineGeneration += 1
        consecutiveEventFailures = 0
        engine = AutoSkipEngine(this) { stage, error ->
            handleEngineError(stage, error)
        }
        if (reason != "connected") {
            recordKeepAliveError("engine_recreate", IllegalStateException(reason))
        }
    }

    private fun handleServiceError(stage: String, error: Throwable) {
        recordKeepAliveError(stage, error)
        consecutiveEventFailures += 1
        if (consecutiveEventFailures >= MAX_CONSECUTIVE_FAILURES) {
            recreateEngine(stage)
        }
    }

    private fun recordKeepAliveError(stage: String, error: Throwable) {
        healthySinceAt = 0L
        healthErrorCleared = false
        AutoSkipHealth.recordError(this, stage, error, engineGeneration)
    }

    private fun handleEngineError(stage: String, error: Throwable) {
        handleServiceError("engine_$stage", error)
    }

    private fun markHealthy(now: Long = System.currentTimeMillis()) {
        if (healthySinceAt == 0L) healthySinceAt = now
        if (!healthErrorCleared && shouldClearHealthError(healthySinceAt, now, HEALTHY_ERROR_CLEAR_DELAY_MS)) {
            healthErrorCleared = true
            AutoSkipHealth.clearError(this, engineGeneration)
        }
    }

    private fun notificationPackageNameFromEvent(event: AccessibilityEvent): String {
        val eventPackageName = event.packageName?.toString().orEmpty()
            .takeIf { !isIgnoredNotificationPackage(it) }
            .orEmpty()
        val activeWindowPackageName = runCatching {
            rootInActiveWindow?.packageName?.toString().orEmpty()
        }.getOrDefault("")
            .takeIf { !isIgnoredNotificationPackage(it) }
            .orEmpty()
        return selectNotificationTitlePackageName(
            eventType = event.eventType,
            eventPackageName = eventPackageName,
            activeWindowPackageName = activeWindowPackageName
        )
    }

    private fun isNotificationTitleEvent(event: AccessibilityEvent, packageName: String): Boolean {
        return packageName.isNotBlank() && when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_WINDOWS_CHANGED,
            AccessibilityEvent.TYPE_VIEW_FOCUSED -> true
            else -> false
        }
    }

    private fun markNotificationEventIfNeeded(event: AccessibilityEvent, packageName: String) {
        val now = System.currentTimeMillis()
        val serviceKeepAliveEnabled = AutoSkipSettings.serviceKeepAliveEnabled(this)
        val daemonKeepAliveEnabled = AutoSkipSettings.daemonKeepAliveEnabled(this)
        val writeIntervalMs = healthWriteIntervalMs(serviceKeepAliveEnabled, daemonKeepAliveEnabled)
            .takeIf { it > 0L }
            ?: SERVICE_HEARTBEAT_INTERVAL_MS
        if (now - lastHealthWriteAt < writeIntervalMs) return
        lastHealthWriteAt = now
        AutoSkipHealth.markNotificationEvent(
            this,
            engineGeneration,
            event.eventType,
            event.packageName?.toString().orEmpty(),
            packageName
        )
    }

    private fun showRunningNotification(packageName: String? = notificationPackageName) {
        lastNotificationPostAt = System.currentTimeMillis()
        postRunningNotification(this, packageName, engineGeneration)
    }

    private fun updateNotificationForPackage(packageName: String) {
        if (packageName.isBlank() || packageName == notificationPackageName) return
        notificationPackageName = packageName
        if (AutoSkipSettings.serviceKeepAliveEnabled(this)) {
            showRunningNotification(packageName)
        }
    }

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "auto_skip_service"
        private const val NOTIFICATION_ID = 2601
        private const val SERVICE_HEARTBEAT_FRESH_MS = SERVICE_HEARTBEAT_INTERVAL_MS * 2
        private const val MAX_CONSECUTIVE_FAILURES = 3
        @Volatile
        private var current: AutoSkipAccessibilityService? = null

        private fun postRunningNotification(
            context: Context,
            packageName: String?,
            generation: Int = 0
        ) {
            val appContext = context.applicationContext
            createNotificationChannel(appContext)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }

            val intent = Intent(appContext, AutoSkipRulesActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            val pendingIntent = PendingIntent.getActivity(
                appContext,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(appContext, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_auto_skip_notification)
                .setContentTitle(notificationTitle(appContext, packageName))
                .setContentText(appContext.getString(R.string.auto_skip_accessibility_status_on))
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setTimeoutAfter(NOTIFICATION_STALE_TIMEOUT_MS)
                .setShowWhen(false)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()

            runCatching {
                NotificationManagerCompat.from(appContext).notify(NOTIFICATION_ID, notification)
            }.onFailure { error ->
                AutoSkipHealth.recordError(appContext, "notification", error, generation)
            }
        }

        private fun notificationTitle(context: Context, packageName: String?): String {
            val cleanPackageName = packageName?.takeIf { !isIgnoredNotificationPackage(it) }
                ?: AutoSkipHealth.read(context)?.let { state ->
                    state.lastTitlePackageName.takeIf { !isIgnoredNotificationPackage(it) }
                        ?: state.lastEventPackageName.takeIf { !isIgnoredNotificationPackage(it) }
                }
            if (cleanPackageName.isNullOrBlank()) return context.getString(R.string.auto_skip_notification_title)
            return runCatching {
                val appInfo = context.packageManager.getApplicationInfo(cleanPackageName, 0)
                LanguageUtils.localizedAppLabel(context, appInfo)
            }.getOrDefault(cleanPackageName)
        }

        private fun isIgnoredNotificationPackage(packageName: String?): Boolean {
            if (packageName.isNullOrBlank()) return true
            val normalized = packageName.lowercase(Locale.ROOT)
            return normalized == "android" ||
                normalized == "system" ||
                normalized == "com.android.systemui" ||
                normalized == "com.miui.securitycenter" ||
                normalized.endsWith(".systemui") ||
                normalized.endsWith(".permissioncontroller")
        }

        private fun createNotificationChannel(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(NOTIFICATION_CHANNEL_ID) != null) return
            manager.createNotificationChannel(
                NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    context.getString(R.string.auto_skip_notification_channel),
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }

        private fun cancelRunningNotification(context: Context) {
            runCatching { NotificationManagerCompat.from(context.applicationContext).cancel(NOTIFICATION_ID) }
        }

        private fun hasFreshServiceHeartbeat(context: Context): Boolean {
            val health = AutoSkipHealth.read(context) ?: return false
            return isServiceHeartbeatFresh(
                serviceConnected = health.serviceConnected,
                lastHeartbeatAt = health.lastHeartbeatAt,
                now = System.currentTimeMillis(),
                freshWindowMs = SERVICE_HEARTBEAT_FRESH_MS
            )
        }

        fun refreshRunningNotification(context: Context) {
            AutoSkipDaemonManager.writeConfig(context, preserveExistingEnabled = true)
            val service = current
            if (service != null) {
                service.refreshKeepAlive()
                return
            }
            if (AutoSkipSettings.serviceKeepAliveEnabled(context) && hasFreshServiceHeartbeat(context)) {
                postRunningNotification(context, AutoSkipHealth.read(context)?.lastEventPackageName)
            } else {
                cancelRunningNotification(context)
            }
        }
    }
}

internal fun keepAliveHeartbeatRequired(
    serviceKeepAliveEnabled: Boolean,
    daemonKeepAliveEnabled: Boolean
): Boolean {
    return serviceKeepAliveEnabled || daemonKeepAliveEnabled
}

internal fun heartbeatIntervalMs(
    serviceKeepAliveEnabled: Boolean,
    daemonKeepAliveEnabled: Boolean
): Long {
    return if (daemonKeepAliveEnabled) {
        DAEMON_HEARTBEAT_INTERVAL_MS
    } else if (serviceKeepAliveEnabled) {
        SERVICE_HEARTBEAT_INTERVAL_MS
    } else {
        0L
    }
}

internal fun healthWriteIntervalMs(
    serviceKeepAliveEnabled: Boolean,
    daemonKeepAliveEnabled: Boolean
): Long {
    return if (daemonKeepAliveEnabled) {
        DAEMON_HEARTBEAT_INTERVAL_MS
    } else if (serviceKeepAliveEnabled) {
        SERVICE_HEARTBEAT_INTERVAL_MS
    } else {
        0L
    }
}

internal fun shouldClearHealthError(
    healthySinceAt: Long,
    now: Long,
    clearDelayMs: Long
): Boolean {
    return healthySinceAt > 0L && now >= healthySinceAt && now - healthySinceAt >= clearDelayMs
}

internal fun isServiceHeartbeatFresh(
    serviceConnected: Boolean,
    lastHeartbeatAt: Long,
    now: Long,
    freshWindowMs: Long
): Boolean {
    if (!serviceConnected || lastHeartbeatAt <= 0L) return false
    val ageMs = now - lastHeartbeatAt
    return ageMs in 0..freshWindowMs
}

internal fun selectNotificationTitlePackageName(
    eventType: Int,
    eventPackageName: String,
    activeWindowPackageName: String
): String {
    return when (eventType) {
        AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> eventPackageName.ifBlank { activeWindowPackageName }
        AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
        AccessibilityEvent.TYPE_WINDOWS_CHANGED,
        AccessibilityEvent.TYPE_VIEW_FOCUSED -> activeWindowPackageName.ifBlank { eventPackageName }
        else -> ""
    }
}

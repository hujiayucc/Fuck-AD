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
import java.util.concurrent.TimeUnit

class AutoSkipAccessibilityService : AccessibilityService() {
    private lateinit var engine: AutoSkipEngine
    private val heartbeatExecutor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "AutoSkipKeepAlive").apply { isDaemon = true }
    }
    private var notificationPackageName: String? = null
    private var heartbeatStarted = false
    private var engineGeneration = 0
    private var consecutiveEventFailures = 0
    private var lastEventHealthWriteAt = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        current = this
        ensureServiceInfo()
        recreateEngine("connected")
        AutoSkipHealth.markConnected(this, engineGeneration)
        AutoSkipDaemonManager.writeConfig(this, preserveExistingEnabled = true)
        showRunningNotification()
        startHeartbeat()
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
        }.onFailure { error ->
            handleServiceError("event", error)
        }
    }

    override fun onInterrupt() {
        AutoSkipHealth.markHeartbeat(this, engineGeneration)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        AutoSkipHealth.markDisconnected(this, "unbind")
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

    private fun startHeartbeat() {
        if (heartbeatStarted) return
        heartbeatStarted = true
        heartbeatExecutor.scheduleWithFixedDelay(
            {
                runCatching { keepAliveTick() }
                    .onFailure { error -> AutoSkipHealth.recordError(this, "heartbeat", error, engineGeneration) }
            },
            0L,
            HEARTBEAT_INTERVAL_MS,
            TimeUnit.MILLISECONDS
        )
    }

    private fun keepAliveTick() {
        AutoSkipHealth.markHeartbeat(this, engineGeneration)
        AutoSkipDaemonManager.writeConfig(this, preserveExistingEnabled = true)
        if (!AutoSkipSettings.serviceKeepAliveEnabled(this)) return
        if (!::engine.isInitialized) recreateEngine("heartbeat")
        showRunningNotification()
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
            AutoSkipHealth.recordError(this, "engine_recreate", IllegalStateException(reason), engineGeneration)
        }
    }

    private fun handleServiceError(stage: String, error: Throwable) {
        consecutiveEventFailures += 1
        AutoSkipHealth.recordError(this, stage, error, engineGeneration)
        if (consecutiveEventFailures >= MAX_CONSECUTIVE_FAILURES) {
            recreateEngine(stage)
        }
    }

    private fun handleEngineError(stage: String, error: Throwable) {
        handleServiceError("engine_$stage", error)
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
        val titleUnchanged = packageName.isBlank() || packageName == notificationPackageName
        if (now - lastEventHealthWriteAt < EVENT_HEALTH_WRITE_INTERVAL_MS && titleUnchanged) return
        lastEventHealthWriteAt = now
        AutoSkipHealth.markNotificationEvent(
            this,
            engineGeneration,
            event.eventType,
            event.packageName?.toString().orEmpty(),
            packageName
        )
    }

    private fun showRunningNotification(packageName: String? = notificationPackageName) {
        postRunningNotification(this, packageName, engineGeneration)
    }

    private fun updateNotificationForPackage(packageName: String) {
        if (packageName.isBlank() || packageName == notificationPackageName) return
        notificationPackageName = packageName
        AutoSkipHealth.markCurrentPackage(this, engineGeneration, packageName)
        showRunningNotification(packageName)
    }

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "auto_skip_service"
        private const val NOTIFICATION_ID = 2601
        private const val HEARTBEAT_INTERVAL_MS = 10_000L
        private const val SERVICE_HEARTBEAT_FRESH_MS = HEARTBEAT_INTERVAL_MS * 2
        private const val EVENT_HEALTH_WRITE_INTERVAL_MS = 1_000L
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

        private fun hasFreshServiceHeartbeat(context: Context): Boolean {
            val health = AutoSkipHealth.read(context) ?: return false
            if (!health.serviceConnected) return false
            val ageMs = System.currentTimeMillis() - health.lastHeartbeatAt
            return ageMs in 0..SERVICE_HEARTBEAT_FRESH_MS
        }

        fun refreshRunningNotification(context: Context) {
            val service = current
            if (service != null) {
                service.startHeartbeat()
                service.showRunningNotification()
                AutoSkipDaemonManager.writeConfig(service, preserveExistingEnabled = true)
            } else {
                AutoSkipDaemonManager.writeConfig(context, preserveExistingEnabled = true)
                if (!hasFreshServiceHeartbeat(context)) {
                    postRunningNotification(context, AutoSkipHealth.read(context)?.lastEventPackageName)
                }
            }
        }
    }
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

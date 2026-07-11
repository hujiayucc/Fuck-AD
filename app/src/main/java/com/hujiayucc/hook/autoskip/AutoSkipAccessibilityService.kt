package com.hujiayucc.hook.autoskip

import android.Manifest
import android.accessibilityservice.AccessibilityService
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
        recreateEngine("connected")
        AutoSkipHealth.markConnected(this, engineGeneration)
        AutoSkipDaemonManager.writeConfig(this)
        showRunningNotification()
        startHeartbeat()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (!::engine.isInitialized) recreateEngine("event")
        runCatching {
            markEventHealthIfNeeded()
            updateNotificationForPackage(event.packageName?.toString().orEmpty())
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
        AutoSkipDaemonManager.writeConfig(this)
        if (!AutoSkipSettings.serviceKeepAliveEnabled(this)) return
        if (!::engine.isInitialized) recreateEngine("heartbeat")
        showRunningNotification()
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

    private fun markEventHealthIfNeeded() {
        val now = System.currentTimeMillis()
        if (now - lastEventHealthWriteAt < EVENT_HEALTH_WRITE_INTERVAL_MS) return
        lastEventHealthWriteAt = now
        AutoSkipHealth.markEvent(this, engineGeneration)
    }

    private fun showRunningNotification(packageName: String? = notificationPackageName) {
        createNotificationChannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val title = notificationTitle(packageName)
        val intent = Intent(this, AutoSkipRulesActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_auto_skip_notification)
            .setContentTitle(title)
            .setContentText(getString(R.string.auto_skip_accessibility_status_on))
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        runCatching {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
        }.onFailure { error ->
            AutoSkipHealth.recordError(this, "notification", error, engineGeneration)
        }
    }

    private fun updateNotificationForPackage(packageName: String) {
        if (packageName.isBlank() || packageName == notificationPackageName) return
        notificationPackageName = packageName
        showRunningNotification(packageName)
    }

    private fun notificationTitle(packageName: String?): String {
        if (packageName.isNullOrBlank()) return getString(R.string.auto_skip_notification_title)
        return runCatching {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            LanguageUtils.localizedAppLabel(this, appInfo)
        }.getOrDefault(packageName)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(NOTIFICATION_CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                getString(R.string.auto_skip_notification_channel),
                NotificationManager.IMPORTANCE_LOW
            )
        )
    }

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "auto_skip_service"
        private const val NOTIFICATION_ID = 2601
        private const val HEARTBEAT_INTERVAL_MS = 10_000L
        private const val EVENT_HEALTH_WRITE_INTERVAL_MS = 1_000L
        private const val MAX_CONSECUTIVE_FAILURES = 3
        @Volatile
        private var current: AutoSkipAccessibilityService? = null

        fun refreshRunningNotification(context: Context) {
            val service = current
            if (service != null) {
                service.startHeartbeat()
                service.showRunningNotification()
                AutoSkipDaemonManager.writeConfig(service)
            } else {
                NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
            }
        }
    }
}

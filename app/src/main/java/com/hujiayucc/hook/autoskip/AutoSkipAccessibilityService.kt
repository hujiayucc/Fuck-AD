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

class AutoSkipAccessibilityService : AccessibilityService() {
    private lateinit var engine: AutoSkipEngine
    private var notificationPackageName: String? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        current = this
        engine = AutoSkipEngine(this)
        showRunningNotification()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!::engine.isInitialized || event == null) return
        updateNotificationForPackage(event.packageName?.toString().orEmpty())
        engine.onAccessibilityEvent(event)
    }

    override fun onInterrupt() {
    }

    override fun onDestroy() {
        if (current === this) current = null
        NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID)
        if (::engine.isInitialized) engine.shutdown()
        super.onDestroy()
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

        NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
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
        @Volatile
        private var current: AutoSkipAccessibilityService? = null

        fun refreshRunningNotification(context: Context) {
            current?.showRunningNotification() ?: NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
        }
    }
}

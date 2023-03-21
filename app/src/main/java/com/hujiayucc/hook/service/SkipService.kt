package com.hujiayucc.hook.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.accessibilityservice.GestureDescription.StrokeDescription
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.highcapable.yukihookapi.hook.factory.modulePrefs
import com.hujiayucc.hook.BuildConfig
import com.hujiayucc.hook.R
import com.hujiayucc.hook.data.Data
import com.hujiayucc.hook.data.Data.isAccessibilitySettingsOn
import com.hujiayucc.hook.data.Data.localeId
import com.hujiayucc.hook.data.DataConst.CHANNEL_ID
import com.hujiayucc.hook.ui.activity.MainActivity
import com.hujiayucc.hook.utils.Language


class SkipService : AccessibilityService() {
    private var notification: Notification? = null
    private var show = false
    private var localeID = 0
    private var skip_count = 0
    /** 自动点击控件关键词 */
    private val list = arrayListOf(
        "跳过", "Skip", "我知道了", "关闭"
    )

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!show && isAccessibilitySettingsOn("com.hujiayucc.hook.service.SkipService"))
            Toast.makeText(applicationContext, applicationContext?.getString(R.string.service_open_success), Toast.LENGTH_SHORT).show()
        show = true
        start()
        return START_STICKY
    }

    private fun start() {
        createNotificationChannel()
        if (isAccessibilitySettingsOn(this::class.java.canonicalName!!)) {
            notification = createNotification(
                getString(R.string.accessibility_notification).format(skip_count),null)
        } else {
            val intents = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            notification = createNotification(getString(R.string.error_accessibilityservice), intent = intents)
        }
        startForeground(1,notification)
    }

    override fun onInterrupt() {
        start()
    }

    override fun onCreate() {
        checkLanguage()
        super.onCreate()
        start()
    }

    override fun onDestroy() {
        start()
        super.onDestroy()
    }

    private fun checkLanguage() {
        localeID = application.modulePrefs.get(localeId)
        if (localeID == 0) return
        val configuration = resources.configuration
        configuration.setLocale(Language.fromId(localeID))
        resources.updateConfiguration(configuration, resources.displayMetrics)
        val locale = resources.configuration.locale
        if (Language.fromId(localeID) != locale) onConfigurationChanged(configuration)
    }

    /** 创建通知渠道 */
    private fun createNotificationChannel() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelName = getString(R.string.app_name)
        if (notificationManager.getNotificationChannel(CHANNEL_ID) != null) return
        val descriptionText = "常驻通知"
        val channel = NotificationChannel(
            CHANNEL_ID,
            channelName,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = descriptionText
        }
        channel.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        channel.enableLights(false)
        channel.enableVibration(false)
        channel.setSound(null, null)
        channel.setShowBadge(false)
        notificationManager.createNotificationChannel(channel)
    }

    /** 创建常驻通知 */
    private fun createNotification(text: String, intent: Intent?): Notification {
        val notificationIntent: Intent = intent ?: Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(applicationContext, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        localeID = application.modulePrefs.get(localeId)
        if (localeID != 0) onCreate()
        start()
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                for (text in list) {
                    findSkipButtonByText(rootInActiveWindow, text)
                }
            }

            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                for (text in list) {
                    findSkipButtonByText(rootInActiveWindow, text)
                }
            }

            else -> {}
        }
    }

    /** 自动查找启动广告的 “跳过” 控件 */
    private fun findSkipButtonByText(nodeInfo: AccessibilityNodeInfo?, text: String) {
        if (nodeInfo == null) return
        val packageName = nodeInfo.packageName.toString()
        if (packageName.equals(BuildConfig.APPLICATION_ID)) return
        if (packageName.startsWith("android.") or packageName.startsWith("com.android.")) return
        if (!applicationContext.modulePrefs.getBoolean(nodeInfo.packageName.toString(), true)) return
        val list = nodeInfo.findAccessibilityNodeInfosByText(text)
        if (list.isNotEmpty()) {
            for (node in list) {
                load(node)
            }
            return
        }
        if (nodeInfo.text != null) {
            if (!nodeInfo.text.contains(text)) return
            load(nodeInfo)
        }
        nodeInfo.recycle()
        start()
    }

    private fun load(node: AccessibilityNodeInfo) {
        if (!node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                val rect = Rect()
                node.getBoundsInScreen(rect)
                click(rect.centerX(), rect.centerY(),0,10)
            }
        node.recycle()
        if (application.modulePrefs.get(Data.hookTip))
            Toast.makeText(application, getString(R.string.tip_skip_success), Toast.LENGTH_SHORT).show()
        skip_count++
    }

    /**
     * 模拟点击
     *
     * @param start_time 开始时间
     * @param duration 持续时间
     */
    private fun click(X: Int, Y: Int, start_time: Long, duration: Long): Boolean {
        val path = Path()
        path.moveTo(X.toFloat(), Y.toFloat())
        val builder = GestureDescription.Builder().addStroke(StrokeDescription(path, start_time, duration))
        return dispatchGesture(builder.build(), null, null)
    }
}
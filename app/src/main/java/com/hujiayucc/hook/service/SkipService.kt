package com.hujiayucc.hook.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.accessibilityservice.GestureDescription.StrokeDescription
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Path
import android.graphics.Point
import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.hujiayucc.hook.BuildConfig
import com.hujiayucc.hook.R
import com.hujiayucc.hook.data.Data
import com.hujiayucc.hook.data.Data.getConfig
import com.hujiayucc.hook.data.Data.isAccessibilitySettingsOn
import com.hujiayucc.hook.data.Data.localeId
import com.hujiayucc.hook.data.DataConst.CHANNEL_ID
import com.hujiayucc.hook.ui.activity.MainActivity
import com.hujiayucc.hook.utils.Language

@Suppress("DEPRECATION")
@SuppressLint("ALL")
class SkipService : AccessibilityService() {
    private var notification: Notification? = null
    private var localeID = 0
    private var skipCount = 0
    /** 自动点击控件关键词 */
    private val list = arrayListOf(
        "跳过", "Skip", "我知道了", "关闭"
    )

    override fun onCreate() {
        checkLanguage()
        super.onCreate()
        if (isAccessibilitySettingsOn("com.hujiayucc.hook.service.SkipService"))
            Toast.makeText(applicationContext, applicationContext?.getString(R.string.service_open_success), Toast.LENGTH_SHORT).show()
        start()
        guard()
    }

    @Synchronized
    private fun guard() {
        Thread {
            while (true) {
                if (isAccessibilitySettingsOn(SkipService::class.java.canonicalName!!)) {
                    val filter = IntentFilter()
                    filter.addAction("com.hujiayucc.hook.service.StartService")
                    registerReceiver(BootReceiver(), filter)
                    val intent = Intent("com.hujiayucc.hook.service.StartService")
                    sendBroadcast(intent)
                    Thread.sleep(60000)
                }
            }
        }.start()

        Thread {
            while (true) {
                start()
                Thread.sleep(10000)
            }
        }.start()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        start()
        return START_STICKY
    }

    @Synchronized
    private fun start() {
        checkLanguage()
        createNotificationChannel()
        if (isAccessibilitySettingsOn(this::class.java.canonicalName!!)) {
            notification = createNotification(getString(R.string.accessibility_notification).format(skipCount))
        } else {
            notification = createNotification(getString(R.string.close_accessibilityservice))
        }
        startForeground(1,notification)
    }

    override fun onInterrupt() {
        start()
    }

    @Synchronized
    private fun checkLanguage() {
        localeID = if (getConfig(localeId.key) as Int? != null) getConfig(localeId.key) as Int else 0
        val configuration = resources.configuration
        configuration.setLocale(Language.fromId(localeID))
        resources.updateConfiguration(configuration, resources.displayMetrics)
        val locale = resources.configuration.locale
        if (Language.fromId(localeID) != locale) onConfigurationChanged(configuration)
    }

    /** 创建通知渠道 */
    @Synchronized
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
    @Synchronized
    private fun createNotification(text: String): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        notificationIntent.action = Intent.ACTION_MAIN
        notificationIntent.addCategory(Intent.CATEGORY_LAUNCHER)
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

    @Synchronized
    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!isAccessibilitySettingsOn("com.hujiayucc.hook.service.SkipService")) return
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> {
                for (text in list) {
                    findSkipButtonByText(rootInActiveWindow, text)
                }
            }

            else -> {}
        }
    }

    /** 自动查找启动广告的 “跳过” 控件 */
    @Synchronized
    private fun findSkipButtonByText(nodeInfo: AccessibilityNodeInfo?, text: String) {
        if (nodeInfo == null) return
        val packageName = nodeInfo.packageName.toString()
        if (packageName == BuildConfig.APPLICATION_ID) return
        if (packageName.startsWith("android.") or packageName.startsWith("com.android.")) return
        if (getConfig(packageName) as Boolean? == false) return
        val list = nodeInfo.findAccessibilityNodeInfosByText(text)
        if (list.isNotEmpty()) {
            for (node in list) {
                load(node)
            }
        }
        if (nodeInfo.text != null) {
            if (!nodeInfo.text.contains(text)) return
            load(nodeInfo)
        }
    }

    @Synchronized
    private fun load(node: AccessibilityNodeInfo) {
        if (!node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            val rect = Rect()
            node.getBoundsInScreen(rect)
            click(rect,0,20)
        }
    }

    /**
     * 模拟点击
     *
     * @param starttime 开始时间
     * @param duration 持续时间
     */
    @Synchronized
    private fun click(rect: Rect, starttime: Long, duration: Long): Boolean {
        val path = Path()
        val point = Point(
            rect.left + (rect.right - rect.left) / 2,
            rect.top + (rect.bottom - rect.top) / 2
        )
        path.moveTo(point.x.toFloat(), point.y.toFloat())
        val builder = GestureDescription.Builder().addStroke(StrokeDescription(path, starttime, duration))
        return dispatchGesture(builder.build(), object : GestureResultCallback() {
            override fun onCancelled(gestureDescription: GestureDescription?) {
                super.onCancelled(gestureDescription)
            }

            override fun onCompleted(gestureDescription: GestureDescription?) {
                super.onCompleted(gestureDescription)
                skipCount++
                if (getConfig(Data.hookTip.key) as Boolean? == true) Toast.makeText(application, getString(R.string.tip_skip_success), Toast.LENGTH_SHORT).show()
                start()
            }
        }, null)
    }
}
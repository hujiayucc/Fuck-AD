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
import com.hujiayucc.hook.R
import com.hujiayucc.hook.data.Data
import com.hujiayucc.hook.data.Data.getConfig
import com.hujiayucc.hook.data.Data.isAccessibilitySettingsOn
import com.hujiayucc.hook.data.DataConst
import com.hujiayucc.hook.ui.activity.MainActivity
import com.hujiayucc.hook.utils.FindId
import com.hujiayucc.hook.utils.Language
import com.hujiayucc.hook.utils.Log
import java.util.*


@Suppress("DEPRECATION")
@SuppressLint("StaticFieldLeak")
class SkipServicempl(private val service: SkipService) {
    private var packageName: CharSequence? = null
    private val context = service.applicationContext
    private var notification: Notification? = null
    private var localeID = 0
    private var skipCount = 0
    private val textRegx1 = Regex("^[0-9]+[\\ss]*跳过[广告]*\$")
    private val textRegx2 = Regex("^[点击]*跳过[广告]*[\\ss]{0,}[0-9]+\$")
    private val textRegx3 = Regex("跳过*[(（][0-9]*[)）]+\$")
    private var eventTime: Long = 0
    private var time: Long = 0

    init {
        start()
        guard()
        checkLanguage()
        if (context.isAccessibilitySettingsOn("com.hujiayucc.hook.service.SkipService"))
            Toast.makeText(context, context.getString(R.string.service_open_success), Toast.LENGTH_SHORT).show()
    }

    private val blackLIst = arrayOf(
        "android",
        "com.android.systemui",
        "com.miui.home",
        "com.tencent.mobileqq",
        "com.tencent.mm",
        "com.omarea.vtools",
        "com.omarea.gesture",
        "com.android.settings",
        "com.hujiayucc.hook"
    )

    private fun success() {
        skipCount++
        if (context.getConfig(Data.hookTip.key) as Boolean? == true)
            Toast.makeText(context, context.getString(R.string.tip_skip_success), Toast.LENGTH_SHORT).show()
        start()
    }

    fun run(event: AccessibilityEvent) {
        try {
            packageName = event.packageName
            for (name in blackLIst) {
                if (name == packageName) return
            }

            if (context.getConfig(packageName.toString()) as Boolean? == false) return

            eventTime = event.eventTime

            if (event.source?.let { findSkipButtonById(it) } == true) {
                success()
                return
            }

            if (event.source?.let { findSkipButtonByText(it) } == true) {
                success()
            }
        } catch (e : Exception) {
            e.printStackTrace()
        }
    }

    @Synchronized
    fun start() {
        checkLanguage()
        createNotificationChannel()
        if (context.isAccessibilitySettingsOn(SkipService::class.java.canonicalName!!)) {
            notification = createNotification(context.getString(R.string.accessibility_notification).format(skipCount))
        } else {
            notification = createNotification(context.getString(R.string.close_accessibilityservice))
        }
        service.startForeground(1, notification)
    }


    /** 创建常驻通知 */
    @Synchronized
    private fun createNotification(text: String): Notification {
        val notificationIntent = Intent(context, MainActivity::class.java)
        notificationIntent.action = Intent.ACTION_MAIN
        notificationIntent.addCategory(Intent.CATEGORY_LAUNCHER)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(context, DataConst.CHANNEL_ID)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    @Synchronized
    fun checkLanguage() {
        localeID =
            if (context.getConfig(Data.localeId.key) as Int? != null) context.getConfig(Data.localeId.key) as Int else 0
        val configuration = service.resources.configuration
        configuration.setLocale(Language.fromId(localeID))
        service.resources.updateConfiguration(configuration, service.resources.displayMetrics)
        val locale = service.resources.configuration.locale
        if (Language.fromId(localeID) != locale) service.onConfigurationChanged(configuration)
    }

    /** 创建通知渠道 */
    @Synchronized
    private fun createNotificationChannel() {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelName = context.getString(R.string.app_name)
        if (notificationManager.getNotificationChannel(DataConst.CHANNEL_ID) != null) return
        val descriptionText = "常驻通知"
        val channel = NotificationChannel(
            DataConst.CHANNEL_ID,
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

    private fun findSkipButtonById(nodeInfo: AccessibilityNodeInfo) : Boolean {
        val id = FindId.fromPackageName(packageName.toString()) ?: return false
        val list = nodeInfo.findAccessibilityNodeInfosByViewId(id["id"].toString())
        if (list.isNotEmpty()) {
            Thread.sleep(id["wait"] as Long)
            for (node in list) {
                Log.e("当前窗口activity===> ${node.packageName}  ${node.text}  ${node.viewIdResourceName}")
                if (time != eventTime && time - eventTime < 500) {
                    skip(node)
                    time = eventTime
                }
            }
            return true
        }
        return false
    }


    /** 自动查找启动广告的 “跳过” 控件 */
    private fun findSkipButtonByText(nodeInfo: AccessibilityNodeInfo): Boolean {
        val list = nodeInfo.findAccessibilityNodeInfosByText("跳过")
        var result = false
        if (list.isNotEmpty()) {
            for (node in list) {
                val text = node.text.trim().replace(Regex("[\nsS秒]", RegexOption.MULTILINE),"")
                val className = node.className.toString().toLowerCase(Locale.getDefault())
                if (className == "android.widget.textview" || className.toLowerCase(Locale.getDefault()).contains("button")) {
                    if (text == "跳过" || text == "跳过广告" || textRegx1.matches(text) || textRegx2.matches(text) || textRegx3.matches(text)) {
                        if (time != eventTime && time - eventTime < 500) {
                            skip(node)
                            time = eventTime
                        }
                        result = true
                    }
                }
            }
            return result
        }
        return false
    }

    @Synchronized
    private fun skip(node: AccessibilityNodeInfo): Boolean {
        if (!node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            val rect = Rect()
            node.getBoundsInScreen(rect)
            return click(rect)
        }
        return false
    }

    /** 画点击手势 */
    private fun gestureDescription(rect: Rect): GestureDescription {
        val point = Point(
            rect.left + (rect.right - rect.left) / 2,
            rect.top + (rect.bottom - rect.top) / 2
        )
        val builder = GestureDescription.Builder()
        val path = Path()
        path.moveTo(point.x.toFloat(), point.y.toFloat())
        builder.addStroke(StrokeDescription(path, 10L, 30L))
        return builder.build()
    }

    /** 模拟点击 */
    @Synchronized
    private fun click(rect: Rect): Boolean {
        val gestureDescription = gestureDescription(rect)
        val callback = object : AccessibilityService.GestureResultCallback() {
            override fun onCancelled(gestureDescription: GestureDescription?) {
                super.onCancelled(gestureDescription)
            }

            override fun onCompleted(gestureDescription: GestureDescription?) {
                super.onCompleted(gestureDescription)
            }
        }

        return service.dispatchGesture(gestureDescription, callback, null)
    }

    @Synchronized
    fun guard() {
        Thread {
            while (true) {
                if (context.isAccessibilitySettingsOn(SkipService::class.java.canonicalName!!)) {
                    val filter = IntentFilter()
                    filter.addAction("com.hujiayucc.hook.service.StartService")
                    context.registerReceiver(BootReceiver(), filter)
                    val intent = Intent("com.hujiayucc.hook.service.StartService")
                    context.sendBroadcast(intent)
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
}
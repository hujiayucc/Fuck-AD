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
import android.content.pm.PackageManager
import android.graphics.Path
import android.graphics.Point
import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.hujiayucc.hook.R
import com.hujiayucc.hook.data.Data
import com.hujiayucc.hook.data.Data.getBoolean
import com.hujiayucc.hook.data.Data.getConfig
import com.hujiayucc.hook.data.Data.getInt
import com.hujiayucc.hook.data.Data.isAccessibilitySettingsOn
import com.hujiayucc.hook.data.Data.skipCount
import com.hujiayucc.hook.data.DataConst.CHANNEL_ID
import com.hujiayucc.hook.data.DataConst.SERVICE_NAME
import com.hujiayucc.hook.ui.activity.MainActivity
import com.hujiayucc.hook.utils.FindId
import com.hujiayucc.hook.utils.Language
import com.hujiayucc.hook.utils.Log
import java.util.*


@Suppress("DEPRECATION")
class SkipServiceImpl(private val service: SkipService) {
    private var packageName: CharSequence? = null
    private val context = service.applicationContext
    private var notification: Notification? = null
    private var localeID = 0
    private val textRegx1 = Regex("^[0-9]+[\\ss]*跳过[广告]*\$")
    private val textRegx2 = Regex("^[点击]*跳过[广告]*[\\ss]{0,}[0-9]+\$")
    private val textRegx3 = Regex("跳过*[(（][0-9]*[)）]+\$")
    private var eventTime: Long = 0
    private var time: Long = 0

    init {
        createNotificationChannel()
        if (context.isAccessibilitySettingsOn(SERVICE_NAME)) {
            notification = createNotification(context.getString(R.string.accessibility_notification).format(skipCount))
        } else {
            notification = createNotification(context.getString(R.string.close_accessibilityservice))
        }
        service.startForeground(1, notification)
        checkLanguage()
        if (context.isAccessibilitySettingsOn(SERVICE_NAME) && !show)
            Toast.makeText(context, context.getString(R.string.service_open_success), Toast.LENGTH_SHORT).show()
        show = true
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
        if (context.getConfig().getBoolean(Data.hookTip.key, true))
            Toast.makeText(context, context.getString(R.string.tip_skip_success), Toast.LENGTH_SHORT).show()
        refresh()
    }

    fun onAccessibilityEvent(event: AccessibilityEvent) {
        try {
            packageName = event.packageName
            if (packageName.isNullOrEmpty()) return
            for (name in blackLIst) {
                if (name == packageName) return
            }

            if (!context.getConfig().getBoolean(packageName.toString(), true)) return

            try {
                val source = event.source?.findAccessibilityNodeInfosByText("跳")
                if (source != null) {
                    for (l in source) {
                        Log.e("当前窗口activity=> ${l.packageName}  ${l.text}  ${l.viewIdResourceName}")
                    }
                }
            } catch (e: PackageManager.NameNotFoundException) {
                e.printStackTrace()
            }

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

    fun refresh() {
        checkLanguage()
        if (context.isAccessibilitySettingsOn(SERVICE_NAME)) {
            notification = createNotification(context.getString(R.string.accessibility_notification).format(skipCount))
        } else {
            notification = createNotification(context.getString(R.string.close_accessibilityservice))
        }
        service.startForeground(1, notification)
    }

    fun onInterrupt() {
        notification = createNotification(context.getString(R.string.close_accessibilityservice))
        Toast.makeText(context, context.getString(R.string.close_accessibilityservice), Toast.LENGTH_SHORT).show()
        service.startForeground(1, notification)
    }


    /** 创建常驻通知 */
    private fun createNotification(text: String): Notification {
        val notificationIntent = Intent(context, MainActivity::class.java)
        notificationIntent.action = Intent.ACTION_MAIN
        notificationIntent.addCategory(Intent.CATEGORY_LAUNCHER)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun checkLanguage() {
        localeID = context.getConfig().getInt(Data.localeId.key, 0)
        val configuration = service.resources.configuration
        configuration.setLocale(Language.fromId(localeID))
        service.resources.updateConfiguration(configuration, service.resources.displayMetrics)
        val locale = service.resources.configuration.locale
        if (Language.fromId(localeID) != locale) service.onConfigurationChanged(configuration)
    }

    /** 创建通知渠道 */
    private fun createNotificationChannel() {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (notificationManager.getNotificationChannel(CHANNEL_ID) != null) return
        val channelName = context.getString(R.string.app_name)
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

    private fun findSkipButtonById(nodeInfo: AccessibilityNodeInfo) : Boolean {
        val id = FindId.fromPackageName(packageName.toString()) ?: return false
        val list = nodeInfo.findAccessibilityNodeInfosByViewId(id["id"].toString())
        if (list.isNotEmpty()) {
            val waitTime = id["wait"] as Long
            Thread.sleep(waitTime)
            for (node in list) {
                Log.e("当前窗口activity===> ${node.packageName}  ${node.text}  ${node.viewIdResourceName}")
                if (time != eventTime && eventTime - time > 800) {
                    skip(node)
                    time = eventTime
                }

                Log.d("${eventTime - time}")
            }
            return true
        }
        return false
    }


    /** 自动查找启动广告的 “跳过” 控件 */
    private fun findSkipButtonByText(nodeInfo: AccessibilityNodeInfo): Boolean {
        var list = nodeInfo.findAccessibilityNodeInfosByText("跳过")
        var result = false
        if (list.isNotEmpty()) {
            for (node in list) {
                val text = node.text.trim().replace(Regex("[\nsS秒]", RegexOption.MULTILINE),"")
                val className = node.className.toString().toLowerCase(Locale.getDefault())
                if (className == "android.widget.textview" || className.toLowerCase(Locale.getDefault()).contains("button")) {
                    if (text == "跳过" || text == "跳过广告" || textRegx1.matches(text) || textRegx2.matches(text) || textRegx3.matches(text)) {
                        if (time != eventTime && eventTime - time > 800) {
                            skip(node)
                            time = eventTime
                            result = true
                        }
                    }
                }
            }
        } else {
            list = nodeInfo.findAccessibilityNodeInfosByText("知道了")
            if (list.isNotEmpty()) {
                for (node in list) {
                    val text = node.text.trim()
                    val className = node.className.toString().toLowerCase(Locale.getDefault())
                    if (className == "android.widget.textview" || className.toLowerCase(Locale.getDefault()).contains("button")) {
                        if (text == "我知道了" || text == "我知道了") {
                            if (time != eventTime && eventTime - time > 800) {
                                skip(node)
                                time = eventTime
                                result = true
                            }
                        }
                    }
                }
            }
        }
        return result
    }

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

    companion object {
        private var show = false
    }
}
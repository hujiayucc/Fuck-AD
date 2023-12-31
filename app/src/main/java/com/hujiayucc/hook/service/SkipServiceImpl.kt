package com.hujiayucc.hook.service

import android.Manifest
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
import android.content.pm.PackageManager
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.hujiayucc.hook.BuildConfig.CHANNEL_ID
import com.hujiayucc.hook.BuildConfig.SERVICE_NAME
import com.hujiayucc.hook.R
import com.hujiayucc.hook.data.Data
import com.hujiayucc.hook.data.Data.getBoolean
import com.hujiayucc.hook.data.Data.getConfig
import com.hujiayucc.hook.data.Data.getInt
import com.hujiayucc.hook.data.Data.isAccessibilitySettingsOn
import com.hujiayucc.hook.data.Data.skipCount
import com.hujiayucc.hook.data.FindId
import com.hujiayucc.hook.ui.activity.MainActivity
import com.hujiayucc.hook.utils.Language
import com.hujiayucc.hook.utils.Log
import java.util.*


@SuppressLint("ForegroundServiceType")
class SkipServiceImpl(private val service: SkipService) {
    private var packageName: CharSequence? = null
    private var notification: Notification? = null
    private var localeID = 0
    private val textRegx1 = Regex("^[0-9]+[\\\\ss]*跳过[广告]*")
    private val textRegx2 = Regex("^[点击]*跳过[广告]*[\\\\ss]{0,}[0-9]+\$")
    private var eventTime: Long = 0
    private var time: Long = 0
    private var findId: FindId? = null

    init {
        runCatching {
            Thread {
                findId = FindId(service)
            }.start()
            createNotificationChannel()
            notification = if (service.isAccessibilitySettingsOn(SERVICE_NAME)) {
                createNotification(service.getString(R.string.accessibility_notification).format(skipCount))
            } else {
                createNotification(service.getString(R.string.close_accessibilityservice))
            }
            service.startForeground(1, notification)
            checkLanguage()
        }
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

    private val blackList2 = arrayOf(
        "com.yaerxing.fkst",
        "com.tencent.wemeet.app"
    )

    private fun success() {
        skipCount++
        if (service.getConfig().getBoolean(Data.hookTip.key, true))
            Toast.makeText(service, service.getString(R.string.tip_skip_success), Toast.LENGTH_SHORT).show()
        refresh()
    }

    fun onAccessibilityEvent(event: AccessibilityEvent) {
        try {
            packageName = event.packageName
            if (packageName.isNullOrEmpty()) return
            for (name in blackLIst) {
                if (name == packageName) return
            }

            if (!service.getConfig().getBoolean(packageName.toString(), true)) return

            eventTime = event.eventTime

            if (event.source?.findSkipButtonById() == true) {
                success()
                return
            }

            if (event.source?.findSkipButtonByText() == true) {
                success()
            }
        } catch (e : Exception) {
            e.printStackTrace()
        }
    }

    fun refresh() {
        checkLanguage()
        notification = if (service.isAccessibilitySettingsOn(SERVICE_NAME)) {
            createNotification(service.getString(R.string.accessibility_notification).format(skipCount))
        } else {
            createNotification(service.getString(R.string.close_accessibilityservice))
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (NotificationManagerCompat.from(service).areNotificationsEnabled()) {
                if (ActivityCompat.checkSelfPermission(service, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
                NotificationManagerCompat.from(service).notify(1, notification!!)
            }
        } else {
            service.startForeground(1, notification)
        }
    }

    fun onInterrupt() {
        refresh()
        Toast.makeText(service, service.getString(R.string.close_accessibilityservice), Toast.LENGTH_SHORT).show()
    }


    /** 创建常驻通知 */
    private fun createNotification(text: String): Notification {
        val notificationIntent = Intent(service, MainActivity::class.java)
        notificationIntent.action = Intent.ACTION_MAIN
        notificationIntent.addCategory(Intent.CATEGORY_LAUNCHER)
        val pendingIntent = PendingIntent.getActivity(
            service,0, notificationIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(service, CHANNEL_ID)
            .setContentTitle(service.getString(R.string.app_name))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    @Suppress("DEPRECATION")
    private fun checkLanguage() {
        localeID = service.getConfig().getInt(Data.localeId.key, 0)
        val configuration = service.resources.configuration
        configuration.setLocale(Language.fromId(localeID))
        service.resources.updateConfiguration(configuration, service.resources.displayMetrics)
        val locale = service.resources.configuration.locale
        if (Language.fromId(localeID) != locale) service.onConfigurationChanged(configuration)
    }

    /** 创建通知渠道 */
    private fun createNotificationChannel() {
        val notificationManager = service.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (notificationManager.getNotificationChannel(CHANNEL_ID) != null) return
        val channelName = service.getString(R.string.app_name)
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

    private fun AccessibilityNodeInfo.findSkipButtonById(): Boolean {
        val id = findId?.findId(packageName) ?: return false
        for (resId in id.resId) {
            val list = findAccessibilityNodeInfosByViewId(resId)
            Log.i(resId)
            var result = false
            if (list.isNotEmpty()) {
                for (node in list) {
                    Log.e("当前窗口activity===> ${node.packageName}  ${node.text}  $id")
                    if (time != eventTime && eventTime - time > 800) {
                        node.skip()
                        result = true
                    }
                    time = eventTime
                }
                return result
            }
        }
        return false
    }

    /** 自动查找启动广告的 “跳过” 控件 */
    private fun AccessibilityNodeInfo.findSkipButtonByText(): Boolean {
        var list = findAccessibilityNodeInfosByText("跳过")
        if (list.isNotEmpty()) {
            for (node in list) {
                val text = node.text.trim().replace(Regex("[\nsS秒]", RegexOption.MULTILINE),"")
                val className = node.className.toString().lowercase(Locale.getDefault())
                if (className == "android.widget.textview" || className.lowercase(Locale.getDefault()).contains("button")) {
                    if (text == "跳过" || text == "跳过广告" || textRegx1.matches(text) || textRegx2.matches(text)) {
                        if (time != eventTime && eventTime - time > 800) {
                            node.skip()
                            time = eventTime
                            return true
                        }
                    }
                }
            }
        }

        list = findAccessibilityNodeInfosByText("知道了")
        if (list.isNotEmpty()) {
            for (node in list) {
                val text = node.text.trim()
                val className = node.className.toString().lowercase(Locale.getDefault())
                if (className == "android.widget.textview" || className.lowercase(Locale.getDefault()).contains("button")) {
                    if (text == "我知道了" || text == "知道了") {
                        if (time != eventTime && eventTime - time > 800) {
                            node.skip()
                            time = eventTime
                            return true
                        }
                    }
                }
            }
        }

        for (name in blackList2) {
            if (name == packageName) return false
        }

        list = findAccessibilityNodeInfosByText("签到")
        if (list.isNotEmpty()) {
            for (node in list) {
                val text = node.text.trim()
                val className = node.className.toString().lowercase(Locale.getDefault())
                if (className == "android.widget.textview" || className.lowercase(Locale.getDefault()).contains("button")) {
                    if (text == "签到") {
                        if (time != eventTime && eventTime - time > 800) {
                            node.skip()
                            time = eventTime
                        }
                    }
                }
            }
        }
        return false
    }

    private fun AccessibilityNodeInfo.skip(): Boolean {
        if (!performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            performAction(AccessibilityNodeInfo.ACTION_CLICK)
            val rect = Rect()
            getBoundsInScreen(rect)
            return click(rect)
        }
        return false
    }

    /** 画点击手势 */
    private fun gestureDescription(rect: Rect): GestureDescription {
        val builder = GestureDescription.Builder()
        val path = Path()
        path.moveTo(rect.centerX().toFloat(), rect.centerY().toFloat())
        path.lineTo(rect.centerX().toFloat(), rect.centerY().toFloat())
        builder.addStroke(StrokeDescription(path, 0, 5))
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
}
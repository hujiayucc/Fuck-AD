package com.hujiayucc.hook.service

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.highcapable.yukihookapi.hook.log.YLog
import com.hujiayucc.hook.R
import com.hujiayucc.hook.data.Data.ACTION
import java.io.InputStream

class ClickService : Service() {
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(1, createNotification())
    }

    private val binder = object : IClickService.Stub() {
        override fun click(activity: String, command: String?) {
            createNotificationChannel()
            startForeground(1, createNotification())
            executeCommand(activity, command)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = binder

    override fun onDestroy() {
        stopSelf()
        super.onDestroy()
    }

    private fun executeCommand(activity: String, command: String?) {
        val pattern = """Window\{.*?\su\d+\s(.*?)\}""".toRegex()
        val currentActivity = pattern.find(currentActivity)?.groupValues?.get(1)
        if (currentActivity?.contains(activity) == false) return
        try {
            process.outputStream.apply {
                write("$command\n".toByteArray())
                flush()
            }
            YLog.info("模拟点击 Activity: $activity 命令: $command")
        } catch (e: Exception) {
            YLog.error("命令执行失败", e)
        }
    }

    val currentActivity
        get() = try {
            val available = process.inputStream.available().toLong()
            process.inputStream.skipBytesCompat(available)
            process.outputStream.apply {
                write("dumpsys activity | grep 'mCurrentFocus'\n".toByteArray())
                flush()
            }

            val reader = process.inputStream.bufferedReader()
            val response = buildString {
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    line?.takeIf { it.contains("mCurrentFocus") }?.let {
                        append(it.trim())
                        return@buildString
                    }
                }
            }
            response
        } catch (e: Exception) {
            YLog.error("命令执行失败", e)
            ""
        }

    private fun InputStream.skipBytesCompat(n: Long) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            skipNBytes(n)
        } else {
            var remaining = n
            while (remaining > 0) {
                val skipped = skip(remaining)
                if (skipped <= 0) break
                remaining -= skipped
            }
        }
    }

    /** 创建通知渠道 */
    private fun createNotificationChannel() {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (notificationManager.getNotificationChannel(CHANNEL_ID) != null) return
        val channelName = getString(R.string.app_name)
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
    private fun createNotification(): Notification {
        val notificationIntent = Intent(this, ClickService::class.java)
        notificationIntent.action = ACTION
        val pendingIntent = PendingIntent.getForegroundService(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("点击服务已启动")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    companion object {
        private val process = Runtime.getRuntime().exec("su")
        const val CHANNEL_ID = "skip"
    }
}
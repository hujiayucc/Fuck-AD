package com.hujiayucc.hook.service

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.highcapable.yukihookapi.hook.log.YLog
import com.hujiayucc.hook.R
import com.hujiayucc.hook.ui.activity.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.InputStream

class ClickService : Service() {
    private var isForeground = false
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var process: Process? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onCreate() {
        super.onCreate()
        startForegroundServiceWithNotification()
    }

    private val binder = object : IClickService.Stub() {
        override fun click(activity: String, command: String?) {
            startForegroundServiceWithNotification()
            executeCommand(activity, command)
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        if (!isForeground) startForegroundServiceWithNotification()
        return binder
    }

    override fun onDestroy() {
        serviceScope.cancel()
        process?.destroy()
        process = null
        stopSelf()
        super.onDestroy()
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun executeCommand(activity: String, command: String?) {
        serviceScope.launch {
            val pattern = """Window\{.*?\su\d+\s(.*?)\}""".toRegex()
            val currentActivity = pattern.find(currentActivity)?.groupValues?.get(1)
            if (currentActivity?.contains(activity) == false) return@launch
            try {
                process!!.outputStream.apply {
                    write("$command\n".toByteArray())
                    flush()
                }
                YLog.info("模拟点击 Activity: $activity 命令: $command")
            } catch (e: Exception) {
                YLog.error("命令执行失败", e)
            }
        }
    }

    val currentActivity
        get() = try {
            val available = process!!.inputStream.available().toLong()
            process!!.inputStream.skipBytesCompat(available)
            process!!.outputStream.apply {
                write("dumpsys activity | grep 'mCurrentFocus'\n".toByteArray())
                flush()
            }

            val reader = process!!.inputStream.bufferedReader()
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

    private fun startForegroundServiceWithNotification() {
        if (isForeground) return
        createNotificationChannel()
        startForeground(1, createNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        process = Runtime.getRuntime().exec("su").apply {
            Thread {
                errorStream.copyTo(System.err)
            }.apply {
                isDaemon = true
                start()
            }
        }
        isForeground = true
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
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
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
        const val CHANNEL_ID = "skip"
    }
}
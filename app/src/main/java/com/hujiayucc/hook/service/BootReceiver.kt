package com.hujiayucc.hook.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.highcapable.yukihookapi.hook.log.YLog
import com.hujiayucc.hook.data.Data.ACTION

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_SCREEN_OFF -> {
                val serviceIntent = Intent(context, ClickService::class.java)
                serviceIntent.action = ACTION
                context.stopService(serviceIntent)
            }

            else -> {
                val serviceIntent = Intent(context, ClickService::class.java)
                serviceIntent.action = ACTION
                context.startForegroundService(serviceIntent)
            }
        }

        YLog.info("BootReceiver: ${intent.action}")
    }
}
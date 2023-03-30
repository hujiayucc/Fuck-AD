package com.hujiayucc.hook.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.Intent.ACTION_BOOT_COMPLETED
import com.hujiayucc.hook.utils.Data.runService
import com.hujiayucc.hook.utils.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        when (intent?.action) {
            ACTION_BOOT_COMPLETED,
            "com.hujiayucc.hook.service.StartService" -> {
                context?.runService()
            }

            else -> intent?.action?.let { Log.d("BootReceiver",it) }
        }
    }
}
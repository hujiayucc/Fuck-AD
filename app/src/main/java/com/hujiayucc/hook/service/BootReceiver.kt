package com.hujiayucc.hook.service

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.hujiayucc.hook.data.Data.isAccessibilitySettingsOn
import com.hujiayucc.hook.data.Data.openService

class BootReceiver : BroadcastReceiver() {
    @SuppressLint("UnsafeProtectedBroadcastReceiver")
    override fun onReceive(context: Context?, intent: Intent?) {
        val intents = Intent(context, SkipService::class.java)
        context?.stopService(intents)
        context?.startService(intents)
        if (context?.isAccessibilitySettingsOn(SkipService::class.java.canonicalName!!) == false) openService()
    }
}
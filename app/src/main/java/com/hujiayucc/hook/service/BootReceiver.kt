package com.hujiayucc.hook.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.highcapable.yukihookapi.hook.factory.prefs
import com.hujiayucc.hook.BuildConfig.SERVICE_NAME
import com.hujiayucc.hook.data.Data.ACTION
import com.hujiayucc.hook.data.Data.isAccessibilitySettingsOn
import com.hujiayucc.hook.data.Data.runService

class BootReceiver : BroadcastReceiver() {

     override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION,
            Intent.ACTION_BOOT_COMPLETED -> {
                if (
                    context.prefs().getString("session").isNotBlank() &&
                    !context.isAccessibilitySettingsOn(SERVICE_NAME)
                ) {
                    context.runService()
                }
            }

            else -> {}
        }
    }
}

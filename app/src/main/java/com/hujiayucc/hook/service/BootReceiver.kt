package com.hujiayucc.hook.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.highcapable.yukihookapi.hook.factory.prefs
import com.hujiayucc.hook.utils.Data.action
import com.hujiayucc.hook.utils.Data.runService

public class BootReceiver : BroadcastReceiver() {

     override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            action,
            Intent.ACTION_BOOT_COMPLETED -> {
                if (context.prefs().getLong("deviceQQ", 0) != 0L) context.runService()
            }

            else -> {}
        }
    }
}

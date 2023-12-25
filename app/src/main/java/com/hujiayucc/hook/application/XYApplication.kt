package com.hujiayucc.hook.application

import android.content.Intent
import android.content.IntentFilter
import com.highcapable.yukihookapi.hook.xposed.application.ModuleApplication
import com.hujiayucc.hook.service.BootReceiver
import com.hujiayucc.hook.utils.Data.action


class XYApplication : ModuleApplication() {
    override fun onCreate() {
        super.onCreate()
        val intent = Intent(action)
        registerReceiver(BootReceiver(), IntentFilter())
        sendBroadcast(Intent(action))
    }
}
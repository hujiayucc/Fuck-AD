package com.hujiayucc.hook.application

import android.content.Intent
import android.content.IntentFilter
import android.os.StrictMode
import com.highcapable.yukihookapi.hook.xposed.application.ModuleApplication
import com.hujiayucc.hook.data.Data.ACTION
import com.hujiayucc.hook.service.BootReceiver


class XYApplication : ModuleApplication() {
    override fun onCreate() {
        super.onCreate()
        val policy = StrictMode.ThreadPolicy.Builder().permitAll().build()
        StrictMode.setThreadPolicy(policy)
        registerReceiver(BootReceiver(), IntentFilter())
        sendBroadcast(Intent(ACTION))
    }
}
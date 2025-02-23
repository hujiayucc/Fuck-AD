package com.hujiayucc.hook.application

import android.content.Intent
import android.os.StrictMode
import com.highcapable.yukihookapi.hook.xposed.application.ModuleApplication

class XYApplication : ModuleApplication() {
    override fun onCreate() {
        super.onCreate()
        val policy = StrictMode.ThreadPolicy.Builder().permitAll().build()
        StrictMode.setThreadPolicy(policy)
        sendBroadcast(Intent("com.hujiayucc.hook.action.BOOT"))
    }
}
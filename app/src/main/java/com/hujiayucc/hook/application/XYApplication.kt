package com.hujiayucc.hook.application

import android.content.Intent
import android.content.IntentFilter
import com.highcapable.yukihookapi.hook.xposed.application.ModuleApplication
import com.hujiayucc.hook.service.BootReceiver
import com.hujiayucc.hook.update.Update.checkUpdate
import org.json.JSONObject


class XYApplication : ModuleApplication() {
    private var info: JSONObject? = null
    override fun onCreate() {
        super.onCreate()
        val filter = IntentFilter("com.hujiayucc.hook.service.StartService")
        registerReceiver(BootReceiver(), filter)
        sendBroadcast(Intent("com.hujiayucc.hook.service.StartService"))
        Thread {
            info = checkUpdate()
        }.start()
    }
}
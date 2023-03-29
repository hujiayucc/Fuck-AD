package com.hujiayucc.hook.application

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.highcapable.yukihookapi.hook.xposed.application.ModuleApplication
import com.hujiayucc.hook.hotfix.HotFixUtils
import com.hujiayucc.hook.hotfix.HotFixUtils.Companion.DEX_FILE
import com.hujiayucc.hook.service.BootReceiver


class XYApplication : ModuleApplication() {
    override fun onCreate() {
        super.onCreate()
        val filter = IntentFilter("com.hujiayucc.hook.service.StartService")
        registerReceiver(BootReceiver(), filter)
        sendBroadcast(Intent("com.hujiayucc.hook.service.StartService"))
    }

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        runCatching {
            initHotFix(base!!.classLoader)
        }
    }

    /** 自动加载热更新 */
    private fun initHotFix(classLoader: ClassLoader) {
        if (!DEX_FILE.exists()) {
            DEX_FILE.mkdirs()
        }
        HotFixUtils().doHotFix(classLoader)
    }
}
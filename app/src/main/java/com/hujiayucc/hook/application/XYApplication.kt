package com.hujiayucc.hook.application

import android.content.Intent
import android.content.IntentFilter
import com.highcapable.yukihookapi.hook.xposed.application.ModuleApplication
import com.hujiayucc.hook.service.BootReceiver
import com.hujiayucc.hook.utils.HotFixUtils
import com.hujiayucc.hook.utils.HotFixUtils.Companion.DEX_FILE
import com.hujiayucc.hook.utils.Update.deleteOld
import java.io.File


class XYApplication : ModuleApplication() {
    override fun onCreate() {
        super.onCreate()
        val filter = IntentFilter("com.hujiayucc.hook.service.StartService")
        registerReceiver(BootReceiver(), filter)
        sendBroadcast(Intent("com.hujiayucc.hook.service.StartService"))

        runCatching {
            initHotFix(applicationContext.classLoader)
        }
    }

    /** 自动加载热更新 */
    private fun initHotFix(classLoader: ClassLoader) {
        // 删除旧版热更新
        if (File(DEX_FILE,"2023.dex").exists()) deleteOld(DEX_FILE)
        // 确保热更新文件夹存在
        if (!DEX_FILE.exists()) DEX_FILE.mkdirs()
        HotFixUtils().doHotFix(classLoader)
    }
}
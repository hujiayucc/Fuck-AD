package com.hujiayucc.hook.application

import android.content.Intent
import android.content.IntentFilter
import com.highcapable.yukihookapi.hook.xposed.application.ModuleApplication
import com.hujiayucc.hook.hotfix.HotFixUtils
import com.hujiayucc.hook.hotfix.HotFixUtils.Companion.DEX_DIR
import com.hujiayucc.hook.service.BootReceiver
import java.io.File
import java.io.IOException


class XYApplication : ModuleApplication() {
    override fun onCreate() {
        super.onCreate()
        val filter = IntentFilter("com.hujiayucc.hook.service.StartService")
        registerReceiver(BootReceiver(), filter)
        sendBroadcast(Intent("com.hujiayucc.hook.service.StartService"))
        initHotFix()
    }

    /** 自动加载热更新 */
    private fun initHotFix() {
        val patchDir = File(filesDir, DEX_DIR)
        if (!patchDir.exists()) {
            patchDir.mkdirs()
        }

        try {
            HotFixUtils().doHotFix(applicationContext)
        } catch (e: IllegalAccessException) {
            e.printStackTrace()
        } catch (e: NoSuchFieldException) {
            e.printStackTrace()
        } catch (e: ClassNotFoundException) {
            e.printStackTrace()
        } catch (e : IOException) {
            e.printStackTrace()
        }
    }
}
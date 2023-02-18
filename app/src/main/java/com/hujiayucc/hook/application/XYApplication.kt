package com.hujiayucc.hook.application

import com.highcapable.yukihookapi.hook.xposed.application.ModuleApplication
import com.hujiayucc.hook.utils.Log

class XYApplication : ModuleApplication() {
    override fun onCreate() {
        super.onCreate()
        Log.d("Application onCreate")
    }
}
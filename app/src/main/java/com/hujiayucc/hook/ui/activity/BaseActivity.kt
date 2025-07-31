package com.hujiayucc.hook.ui.activity

import android.content.ComponentCallbacks2
import android.content.res.Configuration
import android.os.Bundle
import androidx.appcompat.app.AppCompatDelegate
import com.highcapable.yukihookapi.hook.xposed.parasitic.activity.base.ModuleAppCompatActivity
import java.util.Locale

abstract class BaseActivity : ModuleAppCompatActivity() {
    private val configChangeListener = object : ComponentCallbacks2 {
        override fun onConfigurationChanged(newConfig: Configuration) {
            if (newConfig.locale != Locale.getDefault()) {
                handleLanguageChange()
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onLowMemory() {
        }

        override fun onTrimMemory(level: Int) {}
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        application.registerComponentCallbacks(configChangeListener)
    }

    override fun onDestroy() {
        super.onDestroy()
        application.unregisterComponentCallbacks(configChangeListener)
    }

    private fun handleLanguageChange() {
        val currentLocale = AppCompatDelegate.getApplicationLocales()[0]
        val systemLocale = Locale.getDefault()
        if (currentLocale != systemLocale) recreate()
    }
}
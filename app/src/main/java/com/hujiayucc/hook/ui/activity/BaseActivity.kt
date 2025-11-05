package com.hujiayucc.hook.ui.activity

import android.content.ComponentCallbacks2
import android.content.res.Configuration
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.highcapable.yukihookapi.hook.xposed.parasitic.activity.proxy.ModuleActivity
import com.hujiayucc.hook.R
import java.util.*

abstract class BaseActivity : AppCompatActivity(), ModuleActivity {
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

    override val moduleTheme get() = R.style.Theme_XYHook
    override fun getClassLoader() = delegate.getClassLoader()

    override fun onCreate(savedInstanceState: Bundle?) {
        delegate.onCreate(savedInstanceState)
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

    override fun onConfigurationChanged(newConfig: Configuration) {
        delegate.onConfigurationChanged(newConfig)
        super.onConfigurationChanged(newConfig)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        delegate.onRestoreInstanceState(savedInstanceState)
        super.onRestoreInstanceState(savedInstanceState)
    }
}
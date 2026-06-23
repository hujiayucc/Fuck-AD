package com.hujiayucc.hook.ui.activity

import android.content.ComponentCallbacks2
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.window.OnBackInvokedDispatcher
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.hujiayucc.hook.application.XYApplication
import io.github.libxposed.service.XposedService
import java.util.*

abstract class BaseActivity<T : Any> : AppCompatActivity(), XYApplication.ServiceStateListener {
    protected var service: XposedService? = null
    private val configChangeListener = object : ComponentCallbacks2 {
        override fun onConfigurationChanged(newConfig: Configuration) {
            if (newConfig.locales[0] != Locale.getDefault()) {
                handleLanguageChange()
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onLowMemory() {}

        override fun onTrimMemory(level: Int) {}
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        delegate.onCreate(savedInstanceState)
        super.onCreate(savedInstanceState)
        application.registerComponentCallbacks(configChangeListener)
        onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT) {
            handleBackPressed()
        }
    }

    override fun onStart() {
        super.onStart()
        XYApplication.addServiceStateListener(this, true)
    }

    override fun onStop() {
        XYApplication.removeServiceStateListener(this)
        super.onStop()
    }

    private fun handleBackPressed() {
        val cls = when (this) {
            is AppInfoActivity -> SDKActivity::class.java
            is SDKActivity -> MainActivity::class.java
            else -> null
        }
        cls?.let {
            val intent = Intent(this, cls).apply {
                flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            startActivity(intent)
        }
        finish()
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

    override fun onServiceStateChanged(service: XposedService?) {
        this.service = service
    }
}
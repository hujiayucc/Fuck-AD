package com.hujiayucc.hook.ui.activity

import android.content.ComponentCallbacks2
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.window.OnBackInvokedDispatcher
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.highcapable.yukihookapi.hook.xposed.parasitic.activity.proxy.ModuleActivity
import com.hujiayucc.hook.R
import com.hujiayucc.hook.databinding.ActivityAppInfoBinding
import com.hujiayucc.hook.databinding.ActivityMainBinding
import com.hujiayucc.hook.databinding.ActivitySdkBinding
import java.lang.reflect.ParameterizedType
import java.util.*

abstract class BaseActivity<T : Any> : AppCompatActivity(), ModuleActivity {
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

    protected val typeToken: Class<T> by lazy {
        val superclass = this::class.java.genericSuperclass
        if (superclass is ParameterizedType) {
            val typeArguments = superclass.actualTypeArguments
            if (typeArguments.isNotEmpty()) {
                typeArguments[0] as Class<T>
            } else {
                throw IllegalStateException("No generic type parameter found")
            }
        } else {
            throw IllegalStateException("No generic type parameter found")
        }
    }

    override val moduleTheme get() = R.style.Theme_XYHook
    override fun getClassLoader() = delegate.getClassLoader()

    override fun onCreate(savedInstanceState: Bundle?) {
        delegate.onCreate(savedInstanceState)
        super.onCreate(savedInstanceState)
        application.registerComponentCallbacks(configChangeListener)
        if (Build.VERSION.SDK_INT >= 33) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT) {
                handleBackPressed()
            }
        } else {
            onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    handleBackPressed()
                }
            })
        }
    }

    private fun handleBackPressed() {
        val cls = when (typeToken) {
            ActivityAppInfoBinding::class.java -> SDKActivity::class.java
            ActivitySdkBinding::class.java -> MainActivity::class.java
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

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        delegate.onRestoreInstanceState(savedInstanceState)
        super.onRestoreInstanceState(savedInstanceState)
    }

    override fun getOnBackInvokedDispatcher(): OnBackInvokedDispatcher {
        return super.getOnBackInvokedDispatcher()
    }
}
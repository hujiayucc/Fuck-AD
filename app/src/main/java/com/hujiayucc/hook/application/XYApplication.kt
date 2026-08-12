package com.hujiayucc.hook.application

import android.app.Application
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import java.util.concurrent.CopyOnWriteArraySet

class XYApplication: Application(), XposedServiceHelper.OnServiceListener {
    companion object {
        @Volatile
        var mService: XposedService? = null
            private set
        private val serviceStateListeners = CopyOnWriteArraySet<ServiceStateListener>()

        private fun dispatchServiceState(listener: ServiceStateListener, service: XposedService?) {
            if (serviceStateListeners.contains(listener)) {
                listener.onServiceStateChanged(service)
            }
        }

        fun addServiceStateListener(listener: ServiceStateListener, notifyImmediately: Boolean) {
            serviceStateListeners.add(listener)
            if (notifyImmediately) {
                dispatchServiceState(listener, mService)
            }
        }

        fun removeServiceStateListener(listener: ServiceStateListener) {
            serviceStateListeners.remove(listener)
        }
    }

    override fun onCreate() {
        super.onCreate()
        XposedServiceHelper.registerListener(this)
        handleAppLocales()
    }

    private fun handleAppLocales() {
        AppCompatDelegate.getApplicationLocales().apply {
            if (isEmpty) {
                val systemLocale = LocaleList.getDefault()[0]
                AppCompatDelegate.setApplicationLocales(
                    LocaleListCompat.forLanguageTags(systemLocale.toLanguageTag())
                )
            }
        }
    }

    private fun notifyServiceStateChanged(service: XposedService?) {
        for (listener in serviceStateListeners) {
            dispatchServiceState(listener, service)
        }
    }

    interface ServiceStateListener {
        fun onServiceStateChanged(service: XposedService?)
    }

    override fun onServiceBind(service: XposedService) {
        mService = service
        notifyServiceStateChanged(mService)
    }

    override fun onServiceDied(service: XposedService) {
        mService = null
        notifyServiceStateChanged(mService)
    }
}
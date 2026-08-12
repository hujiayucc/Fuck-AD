package com.hujiayucc.hook.hooker.util

import android.util.Log
import com.hujiayucc.hook.ModuleMain.Companion.module
import com.hujiayucc.hook.ModuleMain.Companion.prefs

internal object HookerLogger {
    fun info(message: String, throwable: Throwable? = null) {
        module.log(Log.INFO, TAG, message, throwable)
    }

    fun debug(message: String, throwable: Throwable? = null) {
        module.log(Log.DEBUG, TAG, message, throwable)
    }

    fun error(message: String, throwable: Throwable? = null) {
        module.log(Log.ERROR, TAG, message, throwable)
    }

    fun warn(message: String, throwable: Throwable? = null) {
        module.log(Log.WARN, TAG, message, throwable)
    }

    fun hookDebug(message: String) {
        if (isHookLoggingEnabled()) runCatching { debug(message) }
    }

    fun hookError(message: String, throwable: Throwable? = null) {
        if (isHookLoggingEnabled()) runCatching { error(message, throwable) }
    }

    private fun isHookLoggingEnabled(): Boolean {
        return runCatching { prefs.getBoolean("errorLog", false) }.getOrDefault(false)
    }

    private const val TAG = "Fuck AD"
}
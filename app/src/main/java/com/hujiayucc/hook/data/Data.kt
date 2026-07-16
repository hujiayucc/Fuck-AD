package com.hujiayucc.hook.data

import android.content.Context
import android.content.SharedPreferences
import com.hujiayucc.hook.application.XYApplication
import io.github.libxposed.service.XposedService
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Data {
    private const val PREFS_NAME = "config"
    private val prefsLock = Any()
    @Volatile
    private var cachedService: XposedService? = null
    @Volatile
    private var cachedRemotePrefs: SharedPreferences? = null
    @Volatile
    private var cachedLocalPrefs: SharedPreferences? = null

    val Context.prefsBridge: SharedPreferences
        get() {
            val service = XYApplication.mService
            if (service != null) {
                cachedRemotePrefs?.let { prefs ->
                    if (cachedService === service) return prefs
                }
                runCatching {
                    service.getRemotePreferences(PREFS_NAME)
                }.onSuccess { prefs ->
                    cachedService = service
                    cachedRemotePrefs = prefs
                    return prefs
                }
            } else {
                cachedService = null
                cachedRemotePrefs = null
            }
            return localPrefs()
        }

    private fun Context.localPrefs(): SharedPreferences {
        cachedLocalPrefs?.let { return it }
        return synchronized(prefsLock) {
            cachedLocalPrefs ?: applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).also {
                cachedLocalPrefs = it
            }
        }
    }

    fun String.formatTime(pattern: String = "yyyy-MM-dd"): Date {
        val forMat = SimpleDateFormat(pattern, Locale.US)
        return forMat.parse(this)!!
    }
}
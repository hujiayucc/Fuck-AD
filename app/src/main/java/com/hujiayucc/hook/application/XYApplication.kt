package com.hujiayucc.hook.application

import android.os.LocaleList
import android.os.StrictMode
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.highcapable.yukihookapi.hook.factory.prefs
import com.highcapable.yukihookapi.hook.xposed.application.ModuleApplication
import com.highcapable.yukihookapi.hook.xposed.prefs.YukiHookPrefsBridge

class XYApplication : ModuleApplication() {
    override fun onCreate() {
        super.onCreate()
        val policy = StrictMode.ThreadPolicy.Builder().permitAll().build()
        StrictMode.setThreadPolicy(policy)
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
}
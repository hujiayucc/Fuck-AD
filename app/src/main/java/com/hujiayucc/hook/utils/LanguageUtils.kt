package com.hujiayucc.hook.utils

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.res.Configuration
import android.content.res.Resources
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.hujiayucc.hook.ui.activity.MainActivity
import java.util.Locale

object LanguageUtils {

    /** 设置应用语言 */
    fun setAppLanguage(context: Context, locale: Locale) {
        applyLanguage(context, LocaleListCompat.forLanguageTags(locale.toLanguageTag()))
    }

    /** 重置为系统语言 */
    fun resetToSystemLanguage(context: Context) {
        applyLanguage(context, LocaleListCompat.getEmptyLocaleList())
    }

    fun localizedAppLabel(context: Context, appInfo: ApplicationInfo): String {
        val packageManager = context.packageManager
        val labelRes = appInfo.labelRes
        if (labelRes == 0) return appInfo.loadLabel(packageManager).toString()

        return runCatching {
            val appResources = packageManager.getResourcesForApplication(appInfo)
            val configuration = Configuration(appResources.configuration)
            currentLocaleList().takeIf { !it.isEmpty }?.let { locales ->
                configuration.setLocales(android.os.LocaleList.forLanguageTags(locales.toLanguageTags()))
            }
            val localizedResources = Resources(
                appResources.assets,
                appResources.displayMetrics,
                configuration
            )
            localizedResources.getText(labelRes).toString()
        }.getOrElse {
            appInfo.loadLabel(packageManager).toString()
        }
    }

    fun appLanguageSignature(): String {
        return currentLocaleList().toLanguageTags().ifEmpty { "system" }
    }

    private fun currentLocaleList(): LocaleListCompat {
        return AppCompatDelegate.getApplicationLocales().takeIf { !it.isEmpty }
            ?: LocaleListCompat.getDefault()
    }

    private fun applyLanguage(context: Context, locales: LocaleListCompat) {
        if (AppCompatDelegate.getApplicationLocales() == locales) return
        AppCompatDelegate.setApplicationLocales(locales)
        restartApp(context)
    }

    /** 重启应用 */
    private fun restartApp(context: Context) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val options = ActivityOptions.makeCustomAnimation(
            context,
            androidx.appcompat.R.anim.abc_fade_in,
            androidx.appcompat.R.anim.abc_fade_out
        ).toBundle()

        context.startActivity(intent, options)
    }
}
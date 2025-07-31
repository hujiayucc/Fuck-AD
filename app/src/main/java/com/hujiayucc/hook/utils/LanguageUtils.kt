package com.hujiayucc.hook.utils

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.hujiayucc.hook.ui.activity.MainActivity
import java.util.Locale

object LanguageUtils {

    /** 设置应用语言 */
    fun setAppLanguage(context: Context, locale: Locale) {
        AppCompatDelegate.setApplicationLocales(
            LocaleListCompat.forLanguageTags(locale.toLanguageTag())
        )
        restartApp(context)
    }

    /** 重启应用 */
    private fun restartApp(context: Context) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val options =
            ActivityOptions.makeCustomAnimation(
                context,
                androidx.appcompat.R.anim.abc_fade_in,
                androidx.appcompat.R.anim.abc_fade_out
            ).toBundle()

        context.startActivity(intent, options)
    }

    /** 重置为系统语言 */
    fun resetToSystemLanguage(context: Context) {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
        restartApp(context)
    }
}
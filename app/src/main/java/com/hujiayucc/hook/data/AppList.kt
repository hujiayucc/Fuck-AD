package com.hujiayucc.hook.data

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager.NameNotFoundException
import android.graphics.drawable.Drawable
import com.hujiayucc.hook.R
import com.hujiayucc.hook.annotation.Run
import com.hujiayucc.hook.annotation.RunJiaGu
import com.hujiayucc.hook.utils.AnnotationScanner

private data class AppRule(
    val appName: String,
    val packageName: String,
    val versions: List<String>,
    val action: String
)

class AppList(
    private val context: Context
) {
    val appList: MutableList<Item> = mutableListOf()

    init {
        getAppRules(context.applicationContext).forEach { rule ->
            appList.add(rule.toItem())
        }
    }

    private fun AppRule.toItem(): Item {
        return Item(appName, packageName, versions.toTypedArray(), action, getAppIcon(packageName))
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    private fun getAppIcon(packageName: String): Drawable {
        return try {
            context.packageManager.getApplicationIcon(packageName)
        } catch (_: NameNotFoundException) {
            context.resources.getDrawable(R.mipmap.ic_default, null)
        }
    }

    companion object {
        private const val APP_HOOKER_PACKAGE = "com.hujiayucc.hook.hooker.app"
        private val cacheLock = Any()

        @Volatile
        private var cachedAppRules: List<AppRule>? = null

        private fun getAppRules(context: Context): List<AppRule> {
            cachedAppRules?.let { return it }

            return synchronized(cacheLock) {
                cachedAppRules ?: scanAppRules(context).also { cachedAppRules = it }
            }
        }

        private fun scanAppRules(context: Context): List<AppRule> {
            val rules = mutableListOf<AppRule>()
            AnnotationScanner.scanClassesWithAnnotation(
                context,
                APP_HOOKER_PACKAGE,
                arrayListOf(Run::class.java, RunJiaGu::class.java)
            ).forEach { clazz ->
                clazz.annotations.forEach { annotation ->
                    when (annotation) {
                        is Run -> rules.add(annotation.toAppRule())
                        is RunJiaGu -> rules.add(annotation.toAppRule())
                    }
                }
            }
            return rules
        }

        private fun Run.toAppRule(): AppRule {
            return AppRule(appName, packageName, versions.toList(), action)
        }

        private fun RunJiaGu.toAppRule(): AppRule {
            return AppRule(appName, packageName, versions.toList(), action)
        }
    }
}
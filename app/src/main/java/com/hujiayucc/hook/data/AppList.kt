package com.hujiayucc.hook.data

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager.NameNotFoundException
import android.graphics.drawable.Drawable
import android.icu.text.Collator
import com.hujiayucc.hook.R
import com.hujiayucc.hook.annotation.Run
import java.util.Locale

class AppList(
    private val context: Context
) {
    val appList: MutableList<Item> = mutableListOf()

    init {
        AnnotationScanner.scanClassesWithAnnotation(
            context, "com.hujiayucc.hook.hooker", Run::class.java
        ).forEach { clazz ->
            clazz.annotations.forEach { annotation ->
                if (annotation is Run) {
                    val appName = annotation.appName
                    val packageName = annotation.packageName
                    val versions = annotation.versions
                    val action = annotation.action
                    val appIcon: Drawable = getAppIcon(packageName)
                    val item = Item(appName, packageName, versions, action, appIcon)
                    appList.add(item)
                }
            }
        }
        appList.sortWith(compareBy(Collator.getInstance(Locale.CHINA)) { item -> item.appName })
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    private fun getAppIcon(packageName: String): Drawable {
        return try {
            context.packageManager.getApplicationIcon(packageName)
        } catch (_: NameNotFoundException) {
            context.resources.getDrawable(R.mipmap.ic_default, null)
        }
    }
}
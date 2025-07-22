package com.hujiayucc.hook.data

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager.NameNotFoundException
import android.graphics.drawable.Drawable
import android.icu.text.Collator
import com.fasterxml.jackson.databind.json.JsonMapper
import com.hujiayucc.hook.R
import java.util.Locale

class AppList(
    private val context: Context
) {
    val appList: MutableList<Item> = mutableListOf()

    init {
        val json: List<Item> = JsonMapper().readValue(
            context.assets.open("default.json"),
            ItemTypeReference
        )

        json.forEach { item ->
            item.appIcon = getAppIcon(item.packageName)
            appList.add(item)
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
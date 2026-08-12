package com.hujiayucc.hook.data

import android.graphics.drawable.Drawable

data class Item(
    val appName: String,
    val packageName: String,
    val versions: Array<String>,
    val action: String,
    var appIcon: Drawable
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Item

        if (appName != other.appName) return false
        if (packageName != other.packageName) return false
        if (!versions.contentEquals(other.versions)) return false
        if (action != other.action) return false
        if (appIcon != other.appIcon) return false

        return true
    }

    override fun hashCode(): Int {
        var result = appName.hashCode()
        result = 31 * result + packageName.hashCode()
        result = 31 * result + versions.contentHashCode()
        result = 31 * result + action.hashCode()
        result = 31 * result + appIcon.hashCode()
        return result
    }
}
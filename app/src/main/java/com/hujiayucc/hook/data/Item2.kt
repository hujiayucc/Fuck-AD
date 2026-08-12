package com.hujiayucc.hook.data

import android.graphics.drawable.Drawable

data class Item2(
    val appName: String,
    val packageName: String,
    val action: String,
    val sdkIds: List<String>,
    var appIcon: Drawable
)
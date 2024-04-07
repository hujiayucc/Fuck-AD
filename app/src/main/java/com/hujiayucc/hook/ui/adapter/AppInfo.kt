package com.hujiayucc.hook.ui.adapter

import android.annotation.SuppressLint
import android.graphics.drawable.Drawable
import android.widget.Switch

@SuppressLint("UseSwitchCompatOrMaterialCode")
class AppInfo(
    var appIcon: Drawable? = null,
    var appName: CharSequence,
    var packageName: String,
) {
    var switchCheck: Switch? = null
}
package com.hujiayucc.hook.bean

import android.annotation.SuppressLint
import android.graphics.drawable.Drawable
import android.widget.Switch

@SuppressLint("UseSwitchCompatOrMaterialCode")
class AppInfo(
    var app_icon: Drawable? = null,
    var app_name: CharSequence,
    var app_package: String,
) {
    lateinit var switchCheck: Switch
}
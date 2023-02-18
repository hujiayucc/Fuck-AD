package com.hujiayucc.hook.bean

import android.annotation.SuppressLint
import android.graphics.drawable.Drawable

@SuppressLint("UseSwitchCompatOrMaterialCode")
class AppInfo(
    var app_icon: Drawable? = null,
    var app_name: String? = null,
    var app_package: String? = null,
)
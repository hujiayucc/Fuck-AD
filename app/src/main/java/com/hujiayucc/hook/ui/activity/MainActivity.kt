package com.hujiayucc.hook.ui.activity

import android.annotation.SuppressLint
import android.os.Bundle
import com.hujiayucc.hook.BuildConfig.*
import com.hujiayucc.hook.ui.base.BaseActivity
import com.hujiayucc.hook.utils.*
import java.util.*


@SuppressLint("MissingSuperCall")
class MainActivity : BaseActivity() {
    external override fun onCreate(savedInstanceState: Bundle?)

    companion object {
        var searchText = ""
        init {
            System.loadLibrary("fuck_ad")
        }
    }
}
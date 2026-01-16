package com.hujiayucc.hook.hooker.app

import android.annotation.SuppressLint
import com.hujiayucc.hook.annotation.Run
import com.hujiayucc.hook.hooker.util.Base

@Run(
    appName = "虎牙直播",
    packageName = "com.duowan.kiwi",
    action = "开屏广告"
)
object HuYa : Base() {
    @SuppressLint("ResourceType")
    override fun onStart() {
        loadSdk(kw = true)
    }
}
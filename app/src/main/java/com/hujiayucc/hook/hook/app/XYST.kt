package com.hujiayucc.hook.hook.app

import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.hujiayucc.hook.utils.Log

/** 小猿搜题 */
object XYST: YukiBaseHooker() {
    override fun onHook() {
        Log.d("小猿搜题")
    }
}
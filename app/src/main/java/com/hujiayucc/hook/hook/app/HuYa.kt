package com.hujiayucc.hook.hook.app

import android.widget.TextView
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.factory.method
import com.highcapable.yukihookapi.hook.type.android.TextViewClass

object HuYa : YukiBaseHooker() {
    override fun onHook() {
        TextViewClass.method {
            name = "getText"
        }.hook().after {
            val textView = instance<TextView>()
            if (textView.isClickable && result<String>() == "跳过") {
                textView.callOnClick()
            }
        }
    }
}

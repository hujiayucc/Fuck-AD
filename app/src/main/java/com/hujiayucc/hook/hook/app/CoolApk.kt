package com.hujiayucc.hook.hook.app

import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.factory.allMethods
import com.highcapable.yukihookapi.hook.factory.method
import com.highcapable.yukihookapi.hook.type.android.ButtonClass
import com.highcapable.yukihookapi.hook.type.android.ImageViewClass
import com.highcapable.yukihookapi.hook.type.android.TextViewClass
import com.highcapable.yukihookapi.hook.type.java.LongType

object CoolApk : YukiBaseHooker() {
    private val textRegx = Regex("^跳过(.*)[0-9](.*)")
    override fun onHook() {
        ImageViewClass.method {
            name = "getDrawable"
        }.hook().before {
            val imageView = instance<ImageView>()
            if (imageView.id == 0x7f0a03ed && imageView.contentDescription == "关闭") {
                imageView.callOnClick()
            }
        }

        ButtonClass.allMethods { index, method ->
            method.hook().before {
                val button = instance<Button>()
                if (button.id == 0x1020019 && button.text == "关闭") {
                    button.callOnClick()
                }
            }
        }

        TextViewClass.method {
            name = "getText"
        }.hook().after {
            val textView = instance<TextView>()
            if (textView.isClickable && result<String>()?.let { textRegx.matches(it) } == true) {
                textView.callOnClick()
            }
        }

        "com.coolapk.market.manager.ޝ".toClassOrNull()?.method {
            name = "Γ"
            returnType = LongType
        }?.onNoSuchMethod {}?.ignored()?.hook()?.replaceTo(10)
    }
}

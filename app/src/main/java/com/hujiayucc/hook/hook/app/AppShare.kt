package com.hujiayucc.hook.hook.app

import android.widget.ImageView
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.factory.method
import com.highcapable.yukihookapi.hook.type.android.ImageViewClass
import okhttp3.internal.toHexString

/** App分享 */
object AppShare : YukiBaseHooker() {
    override fun onHook() {
        ImageViewClass.method { name = "getDrawable" }.hook().before {
            val imageView = instance<ImageView>()
            if (imageView.isClickable && imageView.id.toHexString() == "ffffffff") {
                imageView.callOnClick()
            }
        }
    }
}
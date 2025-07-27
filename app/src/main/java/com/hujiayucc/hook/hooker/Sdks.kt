package com.hujiayucc.hook.hooker

import android.view.View
import android.widget.TextView
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.factory.method
import com.highcapable.yukihookapi.hook.log.YLog
import com.highcapable.yukihookapi.hook.type.android.ViewClass
import com.hujiayucc.hook.utils.AppInfoUtil.getActivityFromView
import com.hujiayucc.hook.utils.AppInfoUtil.getResourceName

object Sdks : YukiBaseHooker() {
    @OptIn(ExperimentalStdlibApi::class)
    override fun onHook() {
        YLog.debug("无差别禁用广告SDK")
        ViewClass.method { name = "performClick" }
            .hook {
                before {
                    val view = instance as View
                    val id = view.id
                    val resName: String = getResourceName(view, id)
                    val text = if (view is TextView) view.text.toString() else view::class.java.name

                    // 获取当前 Activity
                    val activity = getActivityFromView(view)
                    val activityName = activity?.javaClass?.name ?: "Unknown"

                    // 输出完整信息
                    YLog.debug(
                        """
                    ====== 点击事件详情 ======
                    View ID: 0x${view.id.toHexString()} $resName
                    View 文本: $text
                    所在 Activity: $activityName
                    """.trimIndent()
                    )
                }
            }

        "android.view.View.DeclaredOnClickListener".toClass()
            .method { name = "onClick" }
            .hook {
                before {
                    val view = instance as View
                    val id = view.id
                    val resName: String = getResourceName(view, id)
                    val text = if (view is TextView) view.text.toString() else view::class.java.name

                    // 获取当前 Activity
                    val activity = getActivityFromView(view)
                    val activityName = activity?.javaClass?.name ?: "Unknown"

                    // 输出完整信息
                    YLog.debug(
                        """
                    ====== 点击事件详情 ======
                    View ID: 0x${view.id.toHexString()} $resName
                    View 文本: $text
                    所在 Activity: $activityName
                    """.trimIndent()
                    )
                }
            }
    }
}
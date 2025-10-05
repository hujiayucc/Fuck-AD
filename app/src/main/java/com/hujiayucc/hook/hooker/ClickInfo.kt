package com.hujiayucc.hook.hooker

import android.view.View
import android.widget.TextView
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.factory.method
import com.highcapable.yukihookapi.hook.log.YLog
import com.highcapable.yukihookapi.hook.type.android.ViewClass
import com.hujiayucc.hook.utils.AppInfoUtil.getActivityFromView
import com.hujiayucc.hook.utils.AppInfoUtil.getResourceName

object ClickInfo : YukiBaseHooker() {
    override fun onHook() {
        val click = prefs.getBoolean("clickInfo")
        val stackTrack = prefs.getBoolean("stackTrack")
        if (click || stackTrack) {
            ViewClass.method { name = "performClick" }
                .hook {
                    before {
                        if (click) printInfo(instance as View)
                        if (stackTrack) printStackTrace(Throwable("堆栈信息"))
                    }
                }

            "android.view.View.DeclaredOnClickListener".toClassOrNull()
                ?.method { name = "onClick" }
                ?.hook {
                    before {
                        if (click) printInfo(instance as View)
                        if (stackTrack) printStackTrace(Throwable("堆栈信息"))
                    }
                }
        }
    }

    private fun printStackTrace(throwable: Throwable) {
        YLog.info(e = throwable)
    }

    @OptIn(ExperimentalStdlibApi::class)
    private fun printInfo(view: View) {
        val id = view.id
        val resName: String = getResourceName(view, id)
        val text = if (view is TextView) view.text.toString() else ""

        // 获取当前 Activity
        val activity = getActivityFromView(view)
        val activityName = activity?.javaClass?.name ?: "Unknown"

        // 输出完整信息
        YLog.debug(
            """
                ====== 点击事件详情 ======
                View 类: ${view::class.java.name}
                View ID: 0x${view.id.toHexString()} $resName
                View 文本: $text
                所在 Activity: $activityName
            """.trimIndent()
        )
    }
}
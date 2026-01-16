package com.hujiayucc.hook.hooker.util

import android.view.View
import android.widget.TextView
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.log.YLog
import com.hujiayucc.hook.data.Data.prefsBridge
import com.hujiayucc.hook.utils.AppInfoUtil

object ClickInfo : YukiBaseHooker() {
    override fun onHook() {
        val click = appContext?.prefsBridge?.getBoolean("clickInfo")
        val stackTrack = appContext?.prefsBridge?.getBoolean("stackTrack")
        if (click == true || stackTrack == true) {
            View::class.resolve().firstMethod { name = "performClick" }
                .hook {
                    before {
                        if (click == true) printInfo(instance as View)
                        if (stackTrack == true) printStackTrace(Throwable("堆栈信息"))
                    }
                }

            "android.view.View.DeclaredOnClickListener".toClassOrNull()
                ?.resolve()?.firstMethod { name = "onClick" }
                ?.hook {
                    before {
                        if (click == true) printInfo(instance as View)
                        if (stackTrack == true) printStackTrace(Throwable("堆栈信息"))
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
        val resName: String = AppInfoUtil.getResourceName(view, id)
        val text = if (view is TextView) view.text.toString() else ""

        // 获取当前 Activity
        val activity = AppInfoUtil.getActivityFromView(view)
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
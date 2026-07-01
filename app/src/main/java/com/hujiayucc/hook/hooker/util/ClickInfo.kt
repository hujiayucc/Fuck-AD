package com.hujiayucc.hook.hooker.util

import android.os.SystemClock
import android.view.View
import android.widget.TextView
import com.hujiayucc.hook.ModuleMain
import com.hujiayucc.hook.utils.AppInfoUtil
import io.github.libxposed.api.XposedModuleInterface
import java.lang.ref.WeakReference

object ClickInfo : Hooker() {
    private const val DUPLICATE_CLICK_WINDOW_MS = 32L

    private var lastClickView: WeakReference<View>? = null
    private var lastClickUptime = 0L

    override fun XposedModuleInterface.PackageReadyParam.onPackageReady() {
        View::class.java.method("performClick")
            .hook {
                before { handleClick(instance as View) }
            }

        "android.view.View.DeclaredOnClickListener".toClassOrNull()
            ?.method("onClick")
            ?.hook {
                before { handleClick(instance as View) }
            }
    }

    val click: Boolean get() = ModuleMain.prefs.getBoolean("clickInfo", false)
    val stackTrack: Boolean get() = ModuleMain.prefs.getBoolean("stackTrack", false)

    private fun handleClick(view: View) {
        val shouldPrintInfo = click
        val shouldPrintStackTrace = stackTrack
        if (!shouldPrintInfo && !shouldPrintStackTrace) return
        if (isDuplicateClick(view)) return

        if (shouldPrintInfo) printInfo(view)
        if (shouldPrintStackTrace) printStackTrace(Throwable("堆栈信息"))
    }

    @Synchronized
    private fun isDuplicateClick(view: View): Boolean {
        val now = SystemClock.uptimeMillis()
        val duplicate = lastClickView?.get() === view &&
            now - lastClickUptime in 0L..DUPLICATE_CLICK_WINDOW_MS
        lastClickView = WeakReference(view)
        lastClickUptime = now
        return duplicate
    }

    private fun printStackTrace(throwable: Throwable) {
        logD("StackTrace:", throwable)
    }

    private fun printInfo(view: View) {
        // 输出完整信息
        logD(
            """
                ====== 点击事件详情 ======
                View 类: ${view::class.java.name}
                View 父类：${view.javaClass.superclass?.name ?: "Unknown"}
                View ID: 0x${view.id.toHexString()} ${AppInfoUtil.getResourceName(view, view.id)}
                View 文本: ${if (view is TextView) view.text.toString() else ""}
                所在 Activity: ${AppInfoUtil.getActivityFromView(view)?.javaClass?.name ?: "Unknown"}
            """.trimIndent()
        )
    }
}

package com.hujiayucc.hook.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.accessibilityservice.GestureDescription.StrokeDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import com.highcapable.yukihookapi.hook.factory.modulePrefs
import com.hujiayucc.hook.BuildConfig
import com.hujiayucc.hook.R
import com.hujiayucc.hook.data.Data
import com.hujiayucc.hook.utils.Log

class SkipService : AccessibilityService() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                Log.d("event", "WINDOW_STATE_CHANGED")
                findSkipButtonByText(rootInActiveWindow)
            }

            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                findSkipButtonByText(rootInActiveWindow)
            }

            else -> {}
        }
    }

    override fun onInterrupt() {}

    /** 自动查找启动广告的 “跳过” 控件 */
    private fun findSkipButtonByText(nodeInfo: AccessibilityNodeInfo?) {
        if (nodeInfo == null) return
        if (nodeInfo.packageName.equals(BuildConfig.APPLICATION_ID)) return
        if (!applicationContext.modulePrefs.getBoolean(nodeInfo.packageName.toString(), true)) return
        val list = nodeInfo.findAccessibilityNodeInfosByText("跳过")
        if (list.isNotEmpty()) {
            for (node in list) {
                if (!node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    if (!node.parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                        val rect = Rect()
                        node.getBoundsInScreen(rect)
                        click(rect.centerX(), rect.centerY(),200,20)
                    }
                }
                node.recycle()
                if (applicationContext.modulePrefs.get(Data.hookTip))
                    Toast.makeText(applicationContext, getString(R.string.tip_skip_success), Toast.LENGTH_SHORT).show()
                Log.i("成功跳过")
            }
            return
        }
        if (nodeInfo.text != null) {
            if (!nodeInfo.text.contains("跳过")) return
            if (!nodeInfo.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                val rect = Rect()
                nodeInfo.getBoundsInScreen(rect)
                click(rect.centerX(), rect.centerY(),200,20)
            }
            nodeInfo.recycle()
            if (applicationContext.modulePrefs.get(Data.hookTip))
                Toast.makeText(applicationContext, getString(R.string.tip_skip_success), Toast.LENGTH_SHORT).show()
            Log.i("成功跳过")
            return
        }

        nodeInfo.recycle()
    }

    /** 模拟点击 */
    private fun click(X: Int, Y: Int, start_time: Long, duration: Long): Boolean {
        val path = Path()
        path.moveTo(X.toFloat(), Y.toFloat())
        val builder = GestureDescription.Builder().addStroke(StrokeDescription(path, start_time, duration))
        return dispatchGesture(builder.build(), null, null)
    }
}
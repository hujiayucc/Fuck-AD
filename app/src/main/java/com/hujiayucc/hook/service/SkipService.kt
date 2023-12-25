package com.hujiayucc.hook.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.hujiayucc.hook.BuildConfig.SERVICE_NAME
import com.hujiayucc.hook.utils.Data.isAccessibilitySettingsOn

class SkipService : AccessibilityService() {
    private var serviceImpl: SkipServiceImpl? = null
    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        serviceImpl = SkipServiceImpl(this)
        serviceImpl?.refresh()
        return START_STICKY
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!isAccessibilitySettingsOn(SERVICE_NAME)) return
        val type = event.eventType
        when (type) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> {
                serviceImpl?.onAccessibilityEvent(event)
            }
            else -> {}
        }
    }

    override fun onInterrupt() {
        serviceImpl?.onInterrupt()
    }
}

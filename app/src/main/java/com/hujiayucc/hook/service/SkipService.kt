package com.hujiayucc.hook.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.hujiayucc.hook.utils.Data.isAccessibilitySettingsOn
import com.hujiayucc.hook.BuildConfig.SERVICE_NAME

class SkipService : AccessibilityService() {
    private lateinit var serviceImpl: SkipServiceImpl
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        serviceImpl = SkipServiceImpl(this)
        serviceImpl.refresh()
        return START_STICKY
    }

    override fun onInterrupt() {
        serviceImpl.onInterrupt()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!isAccessibilitySettingsOn(SERVICE_NAME)) return
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> {
                event.also { serviceImpl.onAccessibilityEvent(it) }
                serviceImpl.refresh()
            }

            else -> {}
        }
    }
}
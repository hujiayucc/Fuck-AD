package com.hujiayucc.hook.service

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.hujiayucc.hook.data.Data.isAccessibilitySettingsOn

@Suppress("DEPRECATION")
@SuppressLint("ALL")
class SkipService : AccessibilityService() {
    private lateinit var servicempl: SkipServicempl
    override fun onCreate() {
        super.onCreate()
        servicempl = SkipServicempl(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        servicempl.start()
        return START_STICKY
    }

    override fun onInterrupt() {
        servicempl.start()
    }

    @Synchronized
    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!isAccessibilitySettingsOn("com.hujiayucc.hook.service.SkipService")) return
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> {
                event.also { servicempl.run(it) }
            }

            else -> {}
        }
    }
}
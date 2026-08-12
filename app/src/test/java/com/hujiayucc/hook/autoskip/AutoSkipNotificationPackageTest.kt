package com.hujiayucc.hook.autoskip

import android.view.accessibility.AccessibilityEvent
import org.junit.Assert.assertEquals
import org.junit.Test

class AutoSkipNotificationPackageTest {
    @Test
    fun windowStateChangeUsesEventPackage() {
        assertEquals(
            "com.example.next",
            selectNotificationTitlePackageName(
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
                eventPackageName = "com.example.next",
                activeWindowPackageName = "com.example.previous"
            )
        )
    }

    @Test
    fun windowListChangeUsesCurrentActiveWindow() {
        assertEquals(
            "com.example.next",
            selectNotificationTitlePackageName(
                AccessibilityEvent.TYPE_WINDOWS_CHANGED,
                eventPackageName = "com.android.systemui",
                activeWindowPackageName = "com.example.next"
            )
        )
    }

    @Test
    fun contentEventDoesNotReplaceTitleWithBackgroundPackage() {
        assertEquals(
            "com.example.foreground",
            selectNotificationTitlePackageName(
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
                eventPackageName = "com.example.background",
                activeWindowPackageName = "com.example.foreground"
            )
        )
    }

    @Test
    fun missingActiveWindowFallsBackToEventPackage() {
        assertEquals(
            "com.example.next",
            selectNotificationTitlePackageName(
                AccessibilityEvent.TYPE_VIEW_FOCUSED,
                eventPackageName = "com.example.next",
                activeWindowPackageName = ""
            )
        )
    }

    @Test
    fun filteredSystemWindowFallsBackToAppEventPackage() {
        assertEquals(
            "com.example.next",
            selectNotificationTitlePackageName(
                AccessibilityEvent.TYPE_WINDOWS_CHANGED,
                eventPackageName = "com.example.next",
                activeWindowPackageName = ""
            )
        )
    }
}

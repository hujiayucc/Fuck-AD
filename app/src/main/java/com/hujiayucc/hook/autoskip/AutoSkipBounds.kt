package com.hujiayucc.hook.autoskip

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

internal fun AccessibilityNodeInfo.boundsInScreen(): Rect {
    val bounds = Rect()
    getBoundsInScreen(bounds)
    return bounds
}

internal fun Rect.isReasonableForScreen(screenWidth: Int, screenHeight: Int): Boolean {
    return isReasonableBounds(left, top, right, bottom, screenWidth, screenHeight)
}

internal fun isReasonableBounds(
    left: Int,
    top: Int,
    right: Int,
    bottom: Int,
    screenWidth: Int,
    screenHeight: Int
): Boolean {
    val width = right - left
    val height = bottom - top
    if (width <= 0 || height <= 0) return false
    if (right <= 0 || bottom <= 0 || left >= screenWidth || top >= screenHeight) return false
    return width >= 8 && height >= 8 && width * height <= screenWidth * screenHeight * 0.6f
}

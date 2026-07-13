package com.hujiayucc.hook.hooker.util

import android.os.Handler
import android.os.Looper

internal object HookerRetryScheduler {
    fun post(block: () -> Unit) {
        Handler(Looper.getMainLooper()).post(block)
    }

    fun postDelayed(delayMillis: Long, block: () -> Unit) {
        Handler(Looper.getMainLooper()).postDelayed(block, delayMillis)
    }
}
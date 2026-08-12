package com.hujiayucc.hook.hooker.util

object AuthCachePolicy {
    fun canReuse(
        sameAuthData: Boolean,
        authorized: Boolean,
        expiresAtEpochSeconds: Long,
        nowEpochSeconds: Long
    ): Boolean {
        return sameAuthData && authorized && expiresAtEpochSeconds > nowEpochSeconds
    }
}
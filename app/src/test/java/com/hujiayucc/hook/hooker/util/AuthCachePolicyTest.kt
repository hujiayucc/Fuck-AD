package com.hujiayucc.hook.hooker.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthCachePolicyTest {
    @Test
    fun authorizedCacheBeforeExpiryCanBeReused() {
        assertTrue(AuthCachePolicy.canReuse(true, true, 101L, 100L))
    }

    @Test
    fun cacheExpiresAtBoundary() {
        assertFalse(AuthCachePolicy.canReuse(true, true, 100L, 100L))
    }

    @Test
    fun expiredCacheCannotBeReused() {
        assertFalse(AuthCachePolicy.canReuse(true, true, 99L, 100L))
    }

    @Test
    fun unauthorizedCacheCannotBeReused() {
        assertFalse(AuthCachePolicy.canReuse(true, false, 101L, 100L))
    }

    @Test
    fun changedAuthDataCannotBeReused() {
        assertFalse(AuthCachePolicy.canReuse(false, true, 101L, 100L))
    }
}
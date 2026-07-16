package com.hujiayucc.hook.autoskip

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoSkipServiceKeepAliveTest {
    @Test
    fun keepAliveRequiresSettingAndUnrestrictedBatteryUsage() {
        assertFalse(serviceKeepAliveCondition(configured = false, unrestricted = false))
        assertFalse(serviceKeepAliveCondition(configured = false, unrestricted = true))
        assertFalse(serviceKeepAliveCondition(configured = true, unrestricted = false))
        assertTrue(serviceKeepAliveCondition(configured = true, unrestricted = true))
    }
}
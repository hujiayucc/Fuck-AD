package com.hujiayucc.hook.autoskip

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoSkipAppModeTest {
    private val listedPackages = setOf("com.example.listed")

    @Test
    fun whitelistProcessesOnlyListedApps() {
        assertTrue(
            shouldProcessApp(
                AutoSkipAppMode.WHITELIST,
                listedPackages,
                "com.example.listed"
            )
        )
        assertFalse(
            shouldProcessApp(
                AutoSkipAppMode.WHITELIST,
                listedPackages,
                "com.example.other"
            )
        )
    }

    @Test
    fun blacklistProcessesOnlyUnlistedApps() {
        assertFalse(
            shouldProcessApp(
                AutoSkipAppMode.BLACKLIST,
                listedPackages,
                "com.example.listed"
            )
        )
        assertTrue(
            shouldProcessApp(
                AutoSkipAppMode.BLACKLIST,
                listedPackages,
                "com.example.other"
            )
        )
    }

    @Test
    fun blankPackageIsNeverProcessed() {
        assertFalse(shouldProcessApp(AutoSkipAppMode.WHITELIST, emptySet(), ""))
        assertFalse(shouldProcessApp(AutoSkipAppMode.BLACKLIST, emptySet(), ""))
    }

    @Test
    fun missingPreferenceDefaultsToWhitelist() {
        assertTrue(AutoSkipAppMode.fromPreference(null) == AutoSkipAppMode.WHITELIST)
        assertTrue(AutoSkipAppMode.fromPreference("unknown") == AutoSkipAppMode.WHITELIST)
    }
}

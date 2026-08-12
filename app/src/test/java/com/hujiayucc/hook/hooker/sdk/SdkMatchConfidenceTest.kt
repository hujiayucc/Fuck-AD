package com.hujiayucc.hook.hooker.sdk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SdkMatchConfidenceTest {
    @Test
    fun coreMarkerProducesHighConfidence() {
        assertEquals(SdkMatchConfidence.HIGH, sdkMatchConfidence(1, 0, 0))
    }

    @Test
    fun twoStrongMarkersProduceHighConfidence() {
        assertEquals(SdkMatchConfidence.HIGH, sdkMatchConfidence(0, 2, 0))
    }

    @Test
    fun oneStrongMarkerProducesMediumConfidence() {
        assertEquals(SdkMatchConfidence.MEDIUM, sdkMatchConfidence(0, 1, 3))
    }

    @Test
    fun twoCompatibilityMarkersProduceLowConfidence() {
        assertEquals(SdkMatchConfidence.LOW, sdkMatchConfidence(0, 0, 2))
    }

    @Test
    fun oneCompatibilityMarkerDoesNotMatch() {
        assertNull(sdkMatchConfidence(0, 0, 1))
    }

    @Test
    fun noMarkersDoesNotMatch() {
        assertNull(sdkMatchConfidence(0, 0, 0))
    }
}
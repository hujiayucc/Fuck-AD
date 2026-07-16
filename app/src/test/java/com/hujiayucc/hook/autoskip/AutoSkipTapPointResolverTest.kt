package com.hujiayucc.hook.autoskip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoSkipTapPointResolverTest {
    private val resolver = AutoSkipTapPointResolver(screenWidth = 1080, screenHeight = 2400)

    @Test
    fun centerUsesNearestClickableBounds() {
        val clickableParent = TestTapNode(
            tapBounds = AutoSkipTapBounds(100, 200, 300, 400),
            clickable = true
        )
        val child = TestTapNode(
            parent = clickableParent,
            tapBounds = AutoSkipTapBounds(140, 240, 200, 300),
            clickable = false
        )

        val points = resolver.resolve(child, AutoSkipTapRequest(AutoSkipTapMode.CENTER))

        assertEquals(listOf(AutoSkipTapPoint(200, 300)), points)
    }

    @Test
    fun probeKeepsOwnThenParentOrderAndStopsAtClickableParent() {
        val ignoredAncestor = TestTapNode(
            tapBounds = AutoSkipTapBounds(20, 20, 420, 420),
            clickable = false
        )
        val clickableParent = TestTapNode(
            parent = ignoredAncestor,
            tapBounds = AutoSkipTapBounds(40, 60, 340, 360),
            clickable = true
        )
        val own = TestTapNode(
            parent = clickableParent,
            tapBounds = AutoSkipTapBounds(100, 120, 200, 220),
            clickable = false
        )

        val points = resolver.resolve(own, AutoSkipTapRequest(AutoSkipTapMode.PROBE))

        assertEquals(
            listOf(
                AutoSkipTapPoint(150, 170),
                AutoSkipTapPoint(186, 152),
                AutoSkipTapPoint(186, 188),
                AutoSkipTapPoint(190, 210),
                AutoSkipTapPoint(298, 156)
            ),
            points
        )
    }

    @Test
    fun unreasonableBoundsProduceNoTapPoint() {
        val oversized = TestTapNode(
            tapBounds = AutoSkipTapBounds(0, 0, 1080, 2400),
            clickable = true
        )

        assertTrue(resolver.resolve(oversized, AutoSkipTapRequest(AutoSkipTapMode.CENTER)).isEmpty())
    }

    private data class TestTapNode(
        override val parent: AutoSkipTapNode? = null,
        override val tapBounds: AutoSkipTapBounds,
        override val clickable: Boolean
    ) : AutoSkipTapNode
}

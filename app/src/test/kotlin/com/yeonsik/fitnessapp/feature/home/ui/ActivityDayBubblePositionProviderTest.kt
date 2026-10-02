package com.yeonsik.fitnessapp.feature.home.ui

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.assertEquals
import org.junit.Test

class ActivityDayBubblePositionProviderTest {
    @Test
    fun bubbleSitsAboveAnchorAndPointsToItsCenter() {
        val provider = ActivityDayBubblePositionProvider()

        val position = provider.calculatePosition(
            anchorBounds = IntRect(140, 260, 160, 280),
            windowSize = IntSize(360, 800),
            layoutDirection = LayoutDirection.Ltr,
            popupContentSize = IntSize(180, 60)
        )

        assertEquals(IntOffset(60, 200), position)
        assertEquals(90, provider.arrowCenterX)
    }

    @Test
    fun bubbleStaysWithinHorizontalWindowEdgesAndKeepsPointerAligned() {
        val provider = ActivityDayBubblePositionProvider()

        val leftPosition = provider.calculatePosition(
            anchorBounds = IntRect(0, 100, 20, 120),
            windowSize = IntSize(360, 800),
            layoutDirection = LayoutDirection.Ltr,
            popupContentSize = IntSize(180, 60)
        )
        assertEquals(IntOffset(0, 40), leftPosition)
        assertEquals(10, provider.arrowCenterX)

        val rightPosition = provider.calculatePosition(
            anchorBounds = IntRect(340, 100, 360, 120),
            windowSize = IntSize(360, 800),
            layoutDirection = LayoutDirection.Ltr,
            popupContentSize = IntSize(180, 60)
        )
        assertEquals(IntOffset(180, 40), rightPosition)
        assertEquals(170, provider.arrowCenterX)
    }
}

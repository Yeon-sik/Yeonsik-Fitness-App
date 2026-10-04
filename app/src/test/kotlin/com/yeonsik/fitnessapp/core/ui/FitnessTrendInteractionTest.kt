package com.yeonsik.fitnessapp.core.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import org.junit.Assert.*
import org.junit.Test

class FitnessTrendInteractionTest {
    @Test
    fun nearbyTapsPickTheClosestPointAndEmptySpaceHasNoSelection() {
        val points = listOf(Offset(24f, 80f), Offset(44f, 80f), Offset(276f, 24f))
        assertEquals(0, fitnessTrendHitTest(points, Offset(22f, 88f), 24f))
        assertEquals(1, fitnessTrendHitTest(points, Offset(38f, 80f), 24f))
        assertEquals(2, fitnessTrendHitTest(points, Offset(280f, 30f), 24f))
        assertNull(fitnessTrendHitTest(points, Offset(150f, 120f), 24f))
        assertNull(fitnessTrendHitTest(emptyList(), Offset.Zero, 24f))
    }

    @Test
    fun filteringInvalidValuesKeepsTheSelectedDatePairedWithItsVolume() {
        val model = fitnessTrendPresentation(listOf(
            FitnessTrendPoint("10/1", 100.0, "2026-10-01"),
            FitnessTrendPoint("10/2", Double.NaN, "2026-10-02"),
            FitnessTrendPoint("10/3", 200.0, "2026-10-03")
        ), minimumPoints = 2)
        val offsets = fitnessTrendPointOffsets(model, Size(300f, 180f), 24f)
        assertEquals(2, offsets.size)
        val selected = fitnessTrendHitTest(offsets, offsets.last(), 24f)!!
        assertEquals("2026-10-03", model.finitePoints[selected].detailLabel)
        assertEquals(200.0, model.finitePoints[selected].value!!, 0.0)
        assertTrue(fitnessTrendAccessibilityDescription(model, "kg").contains("2026-10-03 200kg"))
    }

    @Test
    fun singleAndFlatSeriesKeepUsablePointPositionsEvenInASmallPlot() {
        val single = fitnessTrendPresentation(listOf(FitnessTrendPoint(value = 10.0)), minimumPoints = 1)
        assertEquals(listOf(Offset(150f, 90f)), fitnessTrendPointOffsets(single, Size(300f, 180f), 24f))
        assertEquals(listOf(Offset(10f, 5f)), fitnessTrendPointOffsets(single, Size(20f, 10f), 24f))
        val flat = fitnessTrendPresentation(List(3) { FitnessTrendPoint(value = 10.0) })
        assertEquals(listOf(Offset(24f, 90f), Offset(150f, 90f), Offset(276f, 90f)),
            fitnessTrendPointOffsets(flat, Size(300f, 180f), 24f))
    }
}

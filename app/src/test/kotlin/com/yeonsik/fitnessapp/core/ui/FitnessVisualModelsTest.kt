package com.yeonsik.fitnessapp.core.ui

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FitnessVisualModelsTest {
    @Test
    fun semanticStatusesHaveNonColorPresentation() {
        assertEquals("완료", FitnessSemanticStatus.SUCCESS.label())
        assertEquals("✓", FitnessSemanticStatus.SUCCESS.glyph())
        assertEquals(FitnessSemanticTone.NEUTRAL, FitnessSemanticStatus.UNKNOWN.tone())
        assertEquals(FitnessSemanticStatus.WARNING, FitnessTrendState.INSUFFICIENT.status())
    }

    @Test
    fun countProgressClampsInvalidBoundsAndRepresentsEmpty() {
        val belowZero = fitnessProgressPresentation(completed = -2, total = 4)
        assertEquals(0f, belowZero.fraction)
        assertEquals("0/4", belowZero.label)
        assertFalse(belowZero.isEmpty)

        val aboveTotal = fitnessProgressPresentation(completed = 8, total = 4)
        assertEquals(1f, aboveTotal.fraction)
        assertEquals("4/4", aboveTotal.label)

        val noTotal = fitnessProgressPresentation(completed = 2, total = 0)
        assertEquals(0f, noTotal.fraction)
        assertTrue(noTotal.isEmpty)

        val invalidRatio = fitnessProgressPresentation(Double.NaN, label = "ratio")
        assertTrue(invalidRatio.isEmpty)
        assertEquals(0f, invalidRatio.fraction)

        val largeRatio = fitnessProgressPresentation(1.4, label = "ratio")
        assertEquals(1f, largeRatio.fraction)
    }

    @Test
    fun trendPresentationHandlesEmptyInsufficientSingleFlatAndNormalRanges() {
        val empty = fitnessTrendPresentation(emptyList())
        assertEquals(FitnessTrendState.EMPTY, empty.state)
        assertEquals(FitnessTrendRange(0.0, 1.0), empty.range)

        val single = fitnessTrendPresentation(listOf(FitnessTrendPoint("one", 10.0)))
        assertEquals(FitnessTrendState.INSUFFICIENT, single.state)
        assertTrue(single.range.min < 10.0)
        assertTrue(single.range.max > 10.0)

        val flat = fitnessTrendPresentation(
            listOf(FitnessTrendPoint(value = 5.0), FitnessTrendPoint(value = 5.0), FitnessTrendPoint(value = 5.0))
        )
        assertEquals(FitnessTrendState.READY, flat.state)
        assertTrue(flat.range.span >= 2.0)
        assertEquals(0.5f, flat.range.normalize(5.0), 0.0001f)

        val normal = fitnessTrendPresentation(
            listOf(
                FitnessTrendPoint("A", 10.0),
                FitnessTrendPoint("B", 12.0),
                FitnessTrendPoint("C", 15.0)
            ),
            currentPointIndex = 1
        )
        assertEquals(FitnessTrendState.READY, normal.state)
        assertEquals(1, normal.currentPointIndex)
        assertEquals(listOf(0, 1, 2), normal.finitePointIndices)
        assertTrue(normal.range.normalize(12.0) in 0f..1f)
    }

    @Test
    fun malformedTrendValuesDoNotBecomeDrawableZeroes() {
        val malformed = fitnessTrendPresentation(
            listOf(
                FitnessTrendPoint(value = Double.NaN),
                FitnessTrendPoint(value = Double.POSITIVE_INFINITY),
                FitnessTrendPoint(value = null)
            )
        )
        assertEquals(FitnessTrendState.EMPTY, malformed.state)
        assertTrue(malformed.finitePoints.isEmpty())
        assertEquals("미계산", formatFitnessTrendValue(Double.NaN, "kg"))
    }

    @Test
    fun calendarUsesTheCallerTimezoneBeforeFormattingLocalDate() {
        val instant = Instant.parse("2026-01-01T15:00:00Z").toEpochMilli()
        val utc = fitnessCalendarDateAt(
            instant,
            FitnessCalendarDatePolicy(ZoneId.of("UTC"), Locale.ENGLISH)
        )
        val seoul = fitnessCalendarDateAt(
            instant,
            FitnessCalendarDatePolicy(ZoneId.of("Asia/Seoul"), Locale.KOREAN)
        )
        assertEquals(LocalDate.of(2026, 1, 1), utc)
        assertEquals(LocalDate.of(2026, 1, 2), seoul)
        assertEquals("2026년 9월", formatFitnessMonth(YearMonth.of(2026, 9), Locale.KOREAN))
        assertEquals(YearMonth.of(2026, 8), fitnessCalendarPreviousMonth(YearMonth.of(2026, 9)))
        assertEquals(YearMonth.of(2026, 10), fitnessCalendarNextMonth(YearMonth.of(2026, 9)))
        assertEquals("Mon", fitnessWeekdayLabels(Locale.ENGLISH).first())
    }

    @Test
    fun calendarDayPresentationKeepsInjectedMarkersAndStates() {
        val day = fitnessCalendarDayPresentation(
            date = LocalDate.of(2026, 9, 15),
            displayedMonth = YearMonth.of(2026, 9),
            selectedDate = LocalDate.of(2026, 9, 15),
            today = LocalDate.of(2026, 9, 15),
            markers = listOf(FitnessCalendarMarker("workout", "운동"))
        )
        assertFalse(day.isOutsideDisplayedMonth)
        assertTrue(day.isSelected)
        assertTrue(day.isToday)
        assertEquals(listOf(FitnessCalendarMarker("workout", "운동")), day.markers)
    }

    @Test
    fun confidenceAndSufficiencyRemainQualitative() {
        assertEquals(FitnessSemanticStatus.SUCCESS, FitnessConfidenceLevel.HIGH.status())
        assertEquals(FitnessSemanticStatus.WARNING, FitnessConfidenceLevel.LOW.status())
        assertEquals("알 수 없음", FitnessConfidenceLevel.UNKNOWN.label())
        assertEquals(FitnessSemanticStatus.WARNING, FitnessDataSufficiency.INSUFFICIENT.status())
        assertTrue(FitnessConfidenceLevel.values().all { !it.label().contains("%") })
    }

    @Test
    fun largeFontScaleUsesTheExistingReflowThresholdAndThemesHaveSeparateTokens() {
        assertFalse(shouldStackFitnessMetrics(1.2f))
        assertTrue(shouldStackFitnessMetrics(1.3f))
        assertFalse(shouldStackFitnessMetrics(Float.NaN))
        assertNotEquals(FitnessUiTokens.COLOR_POSITIVE, FitnessUiTokens.COLOR_D_POSITIVE)
        assertNotEquals(FitnessUiTokens.COLOR_BACKGROUND, FitnessUiTokens.COLOR_D_BACKGROUND)
    }
}

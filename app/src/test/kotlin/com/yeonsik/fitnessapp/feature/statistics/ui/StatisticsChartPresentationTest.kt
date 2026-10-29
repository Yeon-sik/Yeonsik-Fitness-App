package com.yeonsik.fitnessapp.feature.statistics.ui

import com.yeonsik.fitness.shared.feature.workout.model.MassUnit
import com.yeonsik.fitnessapp.feature.statistics.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class StatisticsChartPresentationTest {
    private val window = StatisticsPeriodWindow.around(LocalDate.parse("2026-10-21"), StatisticsPeriod.THIRTY_DAYS)
    private fun point(date: String, value: Double?) = StatisticsTrendPoint(date, value, date)

    @Test fun weeklyGapsKeepTheirTimeAndExplicitZeroIsStillAnObservation() {
        val result = statisticsChartEntries(listOf(point("2026-09-21", 0.0), point("2026-09-28", null),
            point("2026-10-05", 80.0), point("2026-10-12", 90.0), point("2026-10-19", null)), window, true)
        assertEquals(listOf(0f, .25f, .5f, .75f, 1f), result.map { it.fraction })
        assertEquals(listOf(false, false, false, true, false), result.map { it.connectsToPrevious })
        assertEquals(0.0, result.first().value!!, 0.0)
        assertNull(result.last().value)
        assertEquals("2026-09-22 ~ 2026-09-27 · 일부 주", result.first().label)
        assertEquals("2026-10-19 ~ 2026-10-21 · 일부 주", result.last().label)
    }

    @Test fun irregularDatesAreNotEquallySpacedOrJoinedAcrossMissingDays() {
        val result = statisticsChartEntries(listOf(point("2026-09-22", 70.0), point("2026-09-23", 71.0),
            point("2026-10-21", 72.0)), window, false)
        assertEquals(1f / 29f, result[1].fraction!!, .00001f)
        assertEquals(1f, result.last().fraction!!, 0f)
        assertTrue(result[1].connectsToPrevious)
        assertFalse(result.last().connectsToPrevious)
    }

    @Test fun invalidDatesAndNonFiniteValuesDoNotFabricatePositionsOrBridgeGaps() {
        val result = statisticsChartEntries(listOf(point("2026-09-22", 1.0), point("invalid", 2.0),
            point("2026-09-23", Double.NaN), point("2026-09-24", 3.0), point("2026-10-22", 4.0)), window, false)
        assertNull(result[1].fraction)
        assertNull(result[2].value)
        assertNull(result[4].fraction)
        assertTrue(result.none { it.connectsToPrevious })
    }

    @Test fun displayUnitConversionDoesNotChangeSourceOrMissingValues() {
        val source = listOf(point("2026-09-22", 10.0), point("2026-09-23", null))
        val result = statisticsChartEntries(source, window, false, MassUnit.LB)
        assertEquals(MassUnit.fromKg(10.0, MassUnit.LB), result.first().value!!, .0001)
        assertEquals(10.0, source.first().value!!, 0.0)
        assertNull(result.last().value)
    }

    @Test fun dayCountsUseDaysAndMissingComparisonStaysUnknown() {
        val days = StatisticsComparisonMetric("운동 기록 일수", 9.0, 14.0, StatisticsValueKind.COUNT)
        assertEquals("9일", statisticsMetricValue(days.current, days, MassUnit.KG))
        assertEquals("-5일", statisticsMetricDifference(days, MassUnit.KG))
        val sessions = days.copy(label = "운동 횟수")
        assertEquals("9회", statisticsMetricValue(sessions.current, sessions, MassUnit.KG))
        assertEquals("비교할 기록 없음", statisticsMetricDifference(days.copy(previous = null), MassUnit.KG))
    }
}

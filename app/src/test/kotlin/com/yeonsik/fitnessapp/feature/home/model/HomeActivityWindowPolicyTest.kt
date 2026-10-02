package com.yeonsik.fitnessapp.feature.home.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.*
import org.junit.Test

class HomeActivityWindowPolicyTest {
    private val today = LocalDate.parse("2026-10-01")
    private val first = LocalDate.parse("2026-01-01")

    @Test fun currentWindowHas91ConsecutiveDaysAnd13MondaySundayWeeks() {
        val window = HomeActivityWindowPolicy.window(today, first)
        val cells = window.cells(emptyMap())
        assertEquals(91, cells.size)
        assertEquals(LocalDate.parse("2026-07-06"), window.start)
        assertEquals(LocalDate.parse("2026-10-04"), window.end)
        assertEquals(today, window.queryEnd)
        assertEquals(DayOfWeek.MONDAY, window.start.dayOfWeek)
        assertEquals(DayOfWeek.SUNDAY, window.end.dayOfWeek)
        assertTrue(today in window.start..window.end)
        cells.zipWithNext().forEach { (a, b) -> assertEquals(a.date.plusDays(1), b.date) }
        assertEquals(13, cells.chunked(7).size)
    }

    @Test fun previousPagesAreAdjacentEvenWhenThereAreNoRecords() {
        val current = HomeActivityWindowPolicy.window(today, first)
        val previous = HomeActivityWindowPolicy.window(today, first, 1)
        assertEquals(current.start.minusWeeks(13), previous.start)
        assertEquals(current.start.minusDays(1), previous.end)
        assertEquals(previous.end, previous.queryEnd)
        assertFalse(current.canGoNext)
        assertTrue(previous.canGoNext)
    }

    @Test fun firstRecordPageIsLastNavigablePageWithExactBoundaryRules() {
        val start = HomeActivityWindowPolicy.window(today, first).start
        assertEquals(0, HomeActivityWindowPolicy.lastPageOffset(today, start))
        assertEquals(1, HomeActivityWindowPolicy.lastPageOffset(today, start.minusDays(1)))
        assertEquals(1, HomeActivityWindowPolicy.lastPageOffset(today, start.minusDays(91)))
        assertEquals(2, HomeActivityWindowPolicy.lastPageOffset(today, start.minusDays(92)))
        val lastOffset = HomeActivityWindowPolicy.lastPageOffset(today, first)
        val last = HomeActivityWindowPolicy.window(today, first, lastOffset)
        assertTrue(first in last.start..last.end)
        assertFalse(last.canGoPrevious)
        assertThrows(IllegalArgumentException::class.java) { HomeActivityWindowPolicy.window(today, first, lastOffset + 1) }
        assertThrows(IllegalArgumentException::class.java) { HomeActivityWindowPolicy.window(today, first, -1) }
    }

    @Test fun beforeTrackingZeroCoverageAndFutureHaveDifferentStatesAndSemantics() {
        val firstDate = LocalDate.parse("2026-09-30")
        val cells = HomeActivityWindowPolicy.window(today, firstDate).cells(mapOf(
            "2026-10-02" to setOf(HomeActivityKind.MEAL)
        )).associateBy { it.date.toString() }
        val before = cells.getValue("2026-09-29")
        val zero = cells.getValue("2026-09-30")
        val future = cells.getValue("2026-10-02")
        assertEquals(HomeActivityCellState.BEFORE_TRACKING, before.state)
        assertNull(HomeActivityCoveragePolicyV1.alpha(before))
        assertEquals(HomeActivityCellState.TRACKED, zero.state)
        assertEquals(0f, HomeActivityCoveragePolicyV1.alpha(zero))
        assertTrue(zero.kinds.isEmpty())
        assertEquals(HomeActivityCellState.FUTURE, future.state)
        assertTrue(future.kinds.isEmpty())
        assertNull(HomeActivityCoveragePolicyV1.alpha(future))
        assertTrue(before.contentDescription.contains("첫 기록 이전"))
        assertTrue(zero.contentDescription.contains("기록 없음, 0종 기록"))
        assertTrue(future.contentDescription.contains("미래 날짜"))
    }

    @Test fun v1ColorsHaveFixedThreeKindMeaning() {
        val exercise = setOf(HomeActivityKind.EXERCISE)
        val two = exercise + HomeActivityKind.WEIGHT
        val three = two + HomeActivityKind.MEAL
        assertEquals(0f, HomeActivityCoveragePolicyV1.alpha(emptySet()), 0f)
        assertEquals(0.33f, HomeActivityCoveragePolicyV1.alpha(exercise), 0f)
        assertEquals(0.66f, HomeActivityCoveragePolicyV1.alpha(two), 0f)
        assertEquals(1f, HomeActivityCoveragePolicyV1.alpha(three), 0f)
        assertEquals(three, HomeActivityCoveragePolicyV1.baselineKinds)
        assertTrue(HomeActivityCell(today, HomeActivityCellState.TRACKED, three).contentDescription
            .contains("운동, 체중, 식단, 3종 기록"))
    }

    @Test fun selectorWindowsIncludeEveryPageFromFirstRecordToCurrent() {
        val windows = HomeActivityWindowPolicy.windows(today, first)
        assertEquals(HomeActivityWindowPolicy.lastPageOffset(today, first) + 1, windows.size)
        assertEquals(0, windows.last().pageOffset)
        assertFalse(windows.first().canGoPrevious)
        windows.zipWithNext().forEach { (a, b) -> assertEquals(a.end.plusDays(1), b.start) }
    }

    @Test fun yearAndLeapDayBoundariesKeepMondayAlignmentAndAllDays() {
        listOf("2024-02-29", "2025-01-01", "2026-12-31", "2026-09-28", "2026-10-04").forEach { date ->
            val window = HomeActivityWindowPolicy.window(LocalDate.parse(date), LocalDate.parse("2020-01-01"))
            assertEquals(91, window.cells(emptyMap()).size)
            assertEquals(DayOfWeek.MONDAY, window.start.dayOfWeek)
            assertEquals(DayOfWeek.SUNDAY, window.end.dayOfWeek)
        }
    }

    @Test fun periodUsesEveryVisibleMonthAndIncludesPartialLatestMonth() {
        val dates = List(81) { LocalDate.parse("2026-06-13").plusDays(it.toLong()) }

        assertEquals(
            listOf("2026-06", "2026-07", "2026-08", "2026-09"),
            HomeActivityWindowPolicy.visibleMonths(dates).map { it.yearMonth.toString() }
        )
        assertEquals("2026 · 6–9월", HomeActivityWindowPolicy.periodLabel(dates.first(), dates.last()))
        assertEquals(YearMonth.parse("2026-09"), HomeActivityWindowPolicy.visibleMonths(dates).last().yearMonth)
    }

    @Test fun crossYearPeriodUsesPaddedYearMonthLabels() {
        assertEquals(
            "2025.12 – 2026.03",
            HomeActivityWindowPolicy.periodLabel(LocalDate.parse("2025-12-15"), LocalDate.parse("2026-03-10"))
        )
    }

    @Test fun collidingMonthLabelsKeepTheLatestVisibleMonth() {
        val dates = List(91) { LocalDate.parse("2025-06-30").plusDays(it.toLong()) }
        val months = HomeActivityWindowPolicy.visibleMonths(dates)
        val labelWidths = months.associate { it.yearMonth to 2 }

        assertEquals("2025 · 6–9월", HomeActivityWindowPolicy.periodLabel(dates.first(), dates.last()))
        val placements = HomeActivityWindowPolicy.placeMonthLabels(months, labelWidths)
        assertTrue(placements.any { it.yearMonth == YearMonth.parse("2025-09") })
        assertFalse(placements.any { it.yearMonth == YearMonth.parse("2025-06") })
        assertTrue(placements.any { it.yearMonth == YearMonth.parse("2025-07") })
    }
}

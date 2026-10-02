package com.yeonsik.fitnessapp.feature.home.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

data class HomeActivityWindow(
    val today: LocalDate,
    val firstRecordedDate: LocalDate,
    val pageOffset: Int,
    val start: LocalDate,
    val end: LocalDate,
    val lastPageOffset: Int
) {
    val queryEnd: LocalDate get() = minOf(end, today)
    val canGoPrevious: Boolean get() = pageOffset < lastPageOffset
    val canGoNext: Boolean get() = pageOffset > 0
    val periodLabel: String get() = HomeActivityWindowPolicy.periodLabel(start, end)

    fun cells(kindsByDate: Map<String, Set<HomeActivityKind>>): List<HomeActivityCell> =
        (0 until HomeActivityWindowPolicy.DAYS).map { offset ->
            val date = start.plusDays(offset.toLong())
            val state = when {
                date > today -> HomeActivityCellState.FUTURE
                date < firstRecordedDate -> HomeActivityCellState.BEFORE_TRACKING
                else -> HomeActivityCellState.TRACKED
            }
            HomeActivityCell(
                date, state,
                if (state == HomeActivityCellState.TRACKED) kindsByDate[date.toString()].orEmpty()
                else emptySet()
            )
        }
}

data class HomeActivityVisibleMonth(
    val yearMonth: YearMonth,
    val firstWeekColumn: Int,
    val lastWeekColumn: Int
)

data class HomeActivityMonthLabelPlacement(
    val yearMonth: YearMonth,
    val firstWeekColumn: Int,
    val columnSpan: Int
)

/** Calendar-only policy. Pages always move by 13 complete Monday–Sunday weeks. */
object HomeActivityWindowPolicy {
    const val WEEKS = 13
    const val DAYS = WEEKS * 7

    /** Includes every YearMonth represented by the actual visible dates, including partial months. */
    fun visibleMonths(dates: List<LocalDate>): List<HomeActivityVisibleMonth> {
        val spans = linkedMapOf<YearMonth, Pair<Int, Int>>()
        dates.forEachIndexed { index, date ->
            val month = YearMonth.from(date)
            val weekColumn = index / 7
            val previous = spans[month]
            spans[month] = if (previous == null) weekColumn to weekColumn
            else previous.first to weekColumn
        }
        return spans.map { (month, span) ->
            HomeActivityVisibleMonth(month, span.first, span.second)
        }
    }

    fun periodLabel(start: LocalDate, end: LocalDate): String {
        val first = YearMonth.from(start)
        val last = YearMonth.from(end)
        return when {
            first == last -> "${first.year} · ${first.monthValue}월"
            first.year == last.year -> "${first.year} · ${first.monthValue}–${last.monthValue}월"
            else -> "${first.year}.${first.monthValue.toString().padStart(2, '0')} – " +
                "${last.year}.${last.monthValue.toString().padStart(2, '0')}"
        }
    }

    /** Places latest months first so collisions can never hide the rightmost visible month. */
    fun placeMonthLabels(
        months: List<HomeActivityVisibleMonth>,
        labelWidthsInColumns: Map<YearMonth, Int>,
        columnCount: Int = WEEKS
    ): List<HomeActivityMonthLabelPlacement> {
        if (columnCount <= 0 || months.isEmpty()) return emptyList()
        val occupied = BooleanArray(columnCount)
        val placements = mutableListOf<HomeActivityMonthLabelPlacement>()
        months.asReversed().forEachIndexed { reverseIndex, month ->
            val span = (labelWidthsInColumns[month.yearMonth] ?: 1).coerceIn(1, columnCount)
            val maxStart = columnCount - span
            val preferredStart = month.firstWeekColumn
            val visibleSpan = month.lastWeekColumn - month.firstWeekColumn + 1
            if (reverseIndex != 0 && span > visibleSpan) return@forEachIndexed
            val start = if (reverseIndex == 0) preferredStart.coerceIn(0, maxStart) else preferredStart
            if (start !in 0..maxStart) return@forEachIndexed
            if ((start until start + span).any { occupied[it] }) return@forEachIndexed
            (start until start + span).forEach { occupied[it] = true }
            placements += HomeActivityMonthLabelPlacement(month.yearMonth, start, span)
        }
        return placements.sortedBy { it.firstWeekColumn }
    }

    fun lastPageOffset(today: LocalDate, firstRecordedDate: LocalDate): Int {
        val currentStart = today.with(DayOfWeek.MONDAY).minusWeeks((WEEKS - 1).toLong())
        val daysBefore = ChronoUnit.DAYS.between(firstRecordedDate, currentStart).coerceAtLeast(0)
        return ((daysBefore + DAYS - 1) / DAYS).toInt()
    }

    fun window(today: LocalDate, firstRecordedDate: LocalDate, pageOffset: Int = 0): HomeActivityWindow {
        val lastPage = lastPageOffset(today, firstRecordedDate)
        require(pageOffset in 0..lastPage) { "Activity page is outside the recorded history." }
        val start = today.with(DayOfWeek.MONDAY)
            .minusWeeks((WEEKS - 1).toLong() + pageOffset.toLong() * WEEKS)
        return HomeActivityWindow(today, firstRecordedDate, pageOffset, start, start.plusDays(DAYS - 1L), lastPage)
    }

    fun windows(today: LocalDate, firstRecordedDate: LocalDate): List<HomeActivityWindow> =
        (lastPageOffset(today, firstRecordedDate) downTo 0).map { window(today, firstRecordedDate, it) }
}

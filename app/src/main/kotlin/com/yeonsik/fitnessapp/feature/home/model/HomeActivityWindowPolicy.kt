package com.yeonsik.fitnessapp.feature.home.model

import java.time.DayOfWeek
import java.time.LocalDate
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
    val periodLabel: String get() = "${start.year}.${start.monthValue}.${start.dayOfMonth} – " +
        "${end.year}.${end.monthValue}.${end.dayOfMonth}"

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

/** Calendar-only policy. Pages always move by 13 complete Monday–Sunday weeks. */
object HomeActivityWindowPolicy {
    const val WEEKS = 13
    const val DAYS = WEEKS * 7

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

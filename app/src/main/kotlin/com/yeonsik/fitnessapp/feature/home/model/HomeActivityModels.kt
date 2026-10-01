package com.yeonsik.fitnessapp.feature.home.model

import java.time.LocalDate

enum class HomeActivityKind(val label: String) {
    EXERCISE("운동"), WEIGHT("체중"), MEAL("식단")
}

enum class HomeActivityCellState { TRACKED, BEFORE_TRACKING, FUTURE }

data class HomeActivityCell(
    val date: LocalDate,
    val state: HomeActivityCellState,
    val kinds: Set<HomeActivityKind> = emptySet()
) {
    val contentDescription: String
        get() {
            val dateLabel = "${date.year}년 ${date.monthValue}월 ${date.dayOfMonth}일"
            return when (state) {
                HomeActivityCellState.BEFORE_TRACKING -> "$dateLabel, 첫 기록 이전"
                HomeActivityCellState.FUTURE -> "$dateLabel, 미래 날짜"
                HomeActivityCellState.TRACKED -> {
                    val names = HomeActivityKind.entries.filter { it in kinds }.joinToString { it.label }
                    if (kinds.isEmpty()) "$dateLabel, 기록 없음, 0종 기록"
                    else "$dateLabel, $names, ${kinds.size}종 기록"
                }
            }
        }
}

data class HomeActivityRecordSummary(
    val kind: HomeActivityKind,
    val name: String? = null,
    val category: String? = null,
    val weightKg: Double? = null
)

data class HomeActivityDayDetails(
    val date: String,
    val records: List<HomeActivityRecordSummary>
) {
    fun recordsFor(kind: HomeActivityKind): List<HomeActivityRecordSummary> =
        records.filter { it.kind == kind }
}

/** Versioned meaning: adding a source must never renormalize historical colors. */
object HomeActivityCoveragePolicyV1 {
    val baselineKinds: Set<HomeActivityKind> = setOf(
        HomeActivityKind.EXERCISE, HomeActivityKind.WEIGHT, HomeActivityKind.MEAL
    )

    fun alpha(kinds: Set<HomeActivityKind>): Float = when (baselineKinds.count { it in kinds }) {
        0 -> 0f
        1 -> 0.33f
        2 -> 0.66f
        else -> 1f
    }

    /** null means the cell is outside tracking, rather than a tracked day with zero coverage. */
    fun alpha(cell: HomeActivityCell): Float? =
        if (cell.state == HomeActivityCellState.TRACKED) alpha(cell.kinds) else null
}

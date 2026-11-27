package com.yeonsik.fitnessapp.core.ui

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Calendar date policy makes the timezone and locale choices explicit. LocalDate and YearMonth
 * themselves are timezone-free; epoch timestamps must be converted with this policy first.
 */
data class FitnessCalendarDatePolicy(
    val zoneId: ZoneId = ZoneId.systemDefault(),
    val locale: Locale = Locale.getDefault()
)

fun fitnessCalendarDateAt(epochMillis: Long, policy: FitnessCalendarDatePolicy): LocalDate =
    Instant.ofEpochMilli(epochMillis).atZone(policy.zoneId).toLocalDate()

fun formatFitnessMonth(month: YearMonth, locale: Locale): String =
    month.format(DateTimeFormatter.ofPattern("yyyy년 M월", locale))

fun fitnessCalendarPreviousMonth(month: YearMonth): YearMonth = month.minusMonths(1)

fun fitnessCalendarNextMonth(month: YearMonth): YearMonth = month.plusMonths(1)

fun fitnessWeekdayLabels(
    locale: Locale,
    firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY
): List<String> = (0 until 7).map { offset ->
    firstDayOfWeek.plus(offset.toLong()).getDisplayName(TextStyle.SHORT, locale)
}

data class FitnessCalendarMarker(
    val key: String,
    val label: String
)

data class FitnessCalendarDayPresentation(
    val date: LocalDate,
    val isOutsideDisplayedMonth: Boolean,
    val isSelected: Boolean,
    val isToday: Boolean,
    val markers: List<FitnessCalendarMarker>
)

fun fitnessCalendarDayPresentation(
    date: LocalDate,
    displayedMonth: YearMonth,
    selectedDate: LocalDate?,
    today: LocalDate,
    markers: Iterable<FitnessCalendarMarker> = emptyList()
): FitnessCalendarDayPresentation = FitnessCalendarDayPresentation(
    date = date,
    isOutsideDisplayedMonth = YearMonth.from(date) != displayedMonth,
    isSelected = date == selectedDate,
    isToday = date == today,
    markers = markers.toList()
)

enum class FitnessConfidenceLevel {
    HIGH,
    MODERATE,
    LOW,
    UNKNOWN
}

fun FitnessConfidenceLevel.label(): String = when (this) {
    FitnessConfidenceLevel.HIGH -> "상대적으로 충분"
    FitnessConfidenceLevel.MODERATE -> "중간 수준"
    FitnessConfidenceLevel.LOW -> "제한적"
    FitnessConfidenceLevel.UNKNOWN -> "알 수 없음"
}

fun FitnessConfidenceLevel.status(): FitnessSemanticStatus = when (this) {
    FitnessConfidenceLevel.HIGH -> FitnessSemanticStatus.SUCCESS
    FitnessConfidenceLevel.MODERATE -> FitnessSemanticStatus.INFO
    FitnessConfidenceLevel.LOW -> FitnessSemanticStatus.WARNING
    FitnessConfidenceLevel.UNKNOWN -> FitnessSemanticStatus.UNKNOWN
}

enum class FitnessDataSufficiency {
    SUFFICIENT,
    INSUFFICIENT,
    UNKNOWN
}

fun FitnessDataSufficiency.label(): String = when (this) {
    FitnessDataSufficiency.SUFFICIENT -> "데이터 충분"
    FitnessDataSufficiency.INSUFFICIENT -> "데이터 부족"
    FitnessDataSufficiency.UNKNOWN -> "데이터 상태 알 수 없음"
}

fun FitnessDataSufficiency.status(): FitnessSemanticStatus = when (this) {
    FitnessDataSufficiency.SUFFICIENT -> FitnessSemanticStatus.SUCCESS
    FitnessDataSufficiency.INSUFFICIENT -> FitnessSemanticStatus.WARNING
    FitnessDataSufficiency.UNKNOWN -> FitnessSemanticStatus.UNKNOWN
}

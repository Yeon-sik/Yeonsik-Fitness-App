package com.yeonsik.fitnessapp.core.ui

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.max

/**
 * Presentation-only status vocabulary shared by visual primitives.
 *
 * The value describes how a UI state should be presented. It does not own the domain rule that
 * produced that state.
 */
enum class FitnessSemanticStatus {
    SUCCESS,
    WARNING,
    ERROR,
    INFO,
    UNKNOWN
}

enum class FitnessSemanticTone {
    SUCCESS,
    WARNING,
    ERROR,
    INFO,
    NEUTRAL
}

fun FitnessSemanticStatus.label(): String = when (this) {
    FitnessSemanticStatus.SUCCESS -> "완료"
    FitnessSemanticStatus.WARNING -> "확인 필요"
    FitnessSemanticStatus.ERROR -> "오류"
    FitnessSemanticStatus.INFO -> "정보"
    FitnessSemanticStatus.UNKNOWN -> "알 수 없음"
}

fun FitnessSemanticStatus.glyph(): String = when (this) {
    FitnessSemanticStatus.SUCCESS -> "✓"
    FitnessSemanticStatus.WARNING -> "!"
    FitnessSemanticStatus.ERROR -> "×"
    FitnessSemanticStatus.INFO -> "i"
    FitnessSemanticStatus.UNKNOWN -> "?"
}

fun FitnessSemanticStatus.tone(): FitnessSemanticTone = when (this) {
    FitnessSemanticStatus.SUCCESS -> FitnessSemanticTone.SUCCESS
    FitnessSemanticStatus.WARNING -> FitnessSemanticTone.WARNING
    FitnessSemanticStatus.ERROR -> FitnessSemanticTone.ERROR
    FitnessSemanticStatus.INFO -> FitnessSemanticTone.INFO
    FitnessSemanticStatus.UNKNOWN -> FitnessSemanticTone.NEUTRAL
}

data class FitnessStatusPresentation(
    val status: FitnessSemanticStatus,
    val label: String = status.label(),
    val message: String? = null
)

/** Ratio/count formatting only; the owner feature still decides what the ratio means. */
data class FitnessProgressPresentation(
    val fraction: Float,
    val label: String,
    val isEmpty: Boolean,
    val completed: Int? = null,
    val total: Int? = null
)

fun fitnessProgressPresentation(
    completed: Int,
    total: Int,
    emptyLabel: String = "진행 없음"
): FitnessProgressPresentation {
    val safeTotal = max(0, total)
    if (safeTotal == 0) {
        return FitnessProgressPresentation(
            fraction = 0f,
            label = emptyLabel,
            isEmpty = true,
            completed = 0,
            total = 0
        )
    }

    val safeCompleted = completed.coerceIn(0, safeTotal)
    return FitnessProgressPresentation(
        fraction = safeCompleted.toFloat() / safeTotal.toFloat(),
        label = "$safeCompleted/$safeTotal",
        isEmpty = false,
        completed = safeCompleted,
        total = safeTotal
    )
}

fun fitnessProgressPresentation(
    ratio: Double,
    label: String,
    empty: Boolean = false,
    emptyLabel: String = "진행 없음"
): FitnessProgressPresentation {
    val validRatio = ratio.isFinite()
    if (empty || !validRatio) {
        return FitnessProgressPresentation(
            fraction = 0f,
            label = emptyLabel,
            isEmpty = true
        )
    }
    return FitnessProgressPresentation(
        fraction = ratio.toFloat().coerceIn(0f, 1f),
        label = label,
        isEmpty = false
    )
}

/** Matches the existing FactRow accessibility threshold without owning a feature layout. */
fun shouldStackFitnessMetrics(fontScale: Float): Boolean =
    fontScale.isFinite() && fontScale >= 1.3f

enum class FitnessTrendScalePolicy {
    ZERO_BASED,
    RANGE_PADDED
}

enum class FitnessTrendState {
    EMPTY,
    INSUFFICIENT,
    READY
}

fun FitnessTrendState.status(): FitnessSemanticStatus = when (this) {
    FitnessTrendState.EMPTY -> FitnessSemanticStatus.UNKNOWN
    FitnessTrendState.INSUFFICIENT -> FitnessSemanticStatus.WARNING
    FitnessTrendState.READY -> FitnessSemanticStatus.SUCCESS
}

fun FitnessTrendState.label(): String = when (this) {
    FitnessTrendState.EMPTY -> "추세 데이터 없음"
    FitnessTrendState.INSUFFICIENT -> "추세 데이터 부족"
    FitnessTrendState.READY -> "추세"
}

data class FitnessTrendPoint(
    val label: String = "",
    val value: Double?
)

data class FitnessTrendRange(
    val min: Double,
    val max: Double
) {
    val span: Double
        get() = max - min

    fun normalize(value: Double): Float {
        if (!value.isFinite() || !min.isFinite() || !max.isFinite() || span <= 0.0) {
            return 0f
        }
        return ((value - min) / span).toFloat().coerceIn(0f, 1f)
    }
}

data class FitnessTrendPresentation(
    val points: List<FitnessTrendPoint>,
    val finitePoints: List<FitnessTrendPoint>,
    val finitePointIndices: List<Int>,
    val state: FitnessTrendState,
    val range: FitnessTrendRange,
    val currentPointIndex: Int?
)

/**
 * Converts already aggregated points into a safe presentation model.
 *
 * No grouping, averaging, or domain calculation belongs here. Null, NaN, and infinite values
 * are excluded from the drawable series so a malformed point cannot create a broken axis.
 */
fun fitnessTrendPresentation(
    points: List<FitnessTrendPoint>,
    minimumPoints: Int = 3,
    scalePolicy: FitnessTrendScalePolicy = FitnessTrendScalePolicy.RANGE_PADDED,
    currentPointIndex: Int? = null
): FitnessTrendPresentation {
    val safePoints = points.toList()
    val finiteIndexed = safePoints.mapIndexedNotNull { index, point ->
        if (point.value?.isFinite() == true) index to point else null
    }
    val finitePoints = finiteIndexed.map { it.second }
    val finitePointIndices = finiteIndexed.map { it.first }
    val requiredPoints = max(1, minimumPoints)
    val state = when {
        finitePoints.isEmpty() -> FitnessTrendState.EMPTY
        finitePoints.size < requiredPoints -> FitnessTrendState.INSUFFICIENT
        else -> FitnessTrendState.READY
    }
    val currentFinitePoint = currentPointIndex?.takeIf { it in finitePointIndices }

    return FitnessTrendPresentation(
        points = safePoints,
        finitePoints = finitePoints,
        finitePointIndices = finitePointIndices,
        state = state,
        range = fitnessTrendRange(finitePoints.mapNotNull { it.value }, scalePolicy),
        currentPointIndex = currentFinitePoint
    )
}

private fun fitnessTrendRange(
    values: List<Double>,
    scalePolicy: FitnessTrendScalePolicy
): FitnessTrendRange {
    if (values.isEmpty()) {
        return FitnessTrendRange(0.0, 1.0)
    }

    val min = values.minOrNull() ?: 0.0
    val rawMax = values.maxOrNull() ?: 1.0
    if (scalePolicy == FitnessTrendScalePolicy.ZERO_BASED) {
        return FitnessTrendRange(0.0, max(1.0, rawMax))
    }

    val rawSpan = max(0.0, rawMax - min)
    if (!rawSpan.isFinite()) {
        return FitnessTrendRange(0.0, 1.0)
    }
    val paddedSpan = max(2.0, rawSpan * 1.2)
    val padding = (paddedSpan - rawSpan) / 2.0
    val paddedMin = min - padding
    val paddedMax = rawMax + padding
    return if (paddedMin.isFinite() && paddedMax.isFinite()) {
        FitnessTrendRange(paddedMin, paddedMax)
    } else {
        FitnessTrendRange(0.0, 1.0)
    }
}

fun formatFitnessTrendValue(value: Double, unit: String = ""): String {
    if (!value.isFinite()) {
        return "미계산"
    }
    return FitnessUiTokens.trimDouble(value) + unit
}

fun fitnessTrendAccessibilityDescription(
    model: FitnessTrendPresentation,
    unit: String = ""
): String = when (model.state) {
    FitnessTrendState.EMPTY -> "추세 데이터 없음"
    FitnessTrendState.INSUFFICIENT -> "추세 데이터 부족"
    FitnessTrendState.READY -> model.finitePoints.mapIndexed { index, point ->
        val label = point.label.ifBlank { "포인트 ${index + 1}" }
        "$label ${formatFitnessTrendValue(point.value ?: Double.NaN, unit)}"
    }.joinToString(", ")
}

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

/** Qualitative review context, never a probability or medical certainty. */
data class FitnessEvidencePresentation(
    val title: String,
    val evidence: String? = null,
    val limitation: String? = null,
    val source: String? = null,
    val confidence: FitnessConfidenceLevel = FitnessConfidenceLevel.UNKNOWN,
    val sufficiency: FitnessDataSufficiency = FitnessDataSufficiency.UNKNOWN
)

package com.yeonsik.fitnessapp.feature.home.ui

import com.yeonsik.fitness.shared.feature.workout.model.MassUnit
import com.yeonsik.fitnessapp.data.MassFormatter
import com.yeonsik.fitnessapp.feature.home.model.HomeActivityDayDetails
import com.yeonsik.fitnessapp.feature.home.model.HomeActivityKind

internal data class HomeActivityPreviewRow(
    val kind: HomeActivityKind,
    val summary: String
)

/** Compact Home-only projection; full session, meal and weight records remain in Records. */
internal fun homeActivityPreviewRows(
    details: HomeActivityDayDetails,
    preferredMassUnit: MassUnit
): List<HomeActivityPreviewRow> {
    val exerciseRecords = details.recordsFor(HomeActivityKind.EXERCISE)
    val mealRecords = details.recordsFor(HomeActivityKind.MEAL)
    val weightRecords = details.recordsFor(HomeActivityKind.WEIGHT)
    val rows = mutableListOf<HomeActivityPreviewRow>()

    if (exerciseRecords.isNotEmpty()) {
        val cardioRecords = exerciseRecords.filter { it.workoutType == "cardio" }
        val strengthRecords = exerciseRecords.filter { it.workoutType == "strength" }
        val otherRecords = exerciseRecords.filter { it.workoutType !in setOf("cardio", "strength") }
        val bodyParts = strengthRecords.flatMap { record ->
            record.category.orEmpty().split(" · ").map(String::trim).filter(String::isNotEmpty)
        }.distinct()
        val strengthSummary = when {
            strengthRecords.isEmpty() -> null
            bodyParts.size > 2 -> "${bodyParts.take(2).joinToString(" · ")} 외 ${bodyParts.size - 2}"
            bodyParts.isNotEmpty() -> bodyParts.joinToString(" · ")
            else -> "근력 운동"
        }
        val cardioSummary = cardioRecords.takeIf { it.isNotEmpty() }?.let { records ->
            val totalSeconds = records.sumOf { it.durationSeconds?.coerceAtLeast(0) ?: 0 }
            if (totalSeconds > 0) "유산소 ${totalSeconds / 60}분" else "유산소 기록"
        }
        val otherSummary = otherRecords.takeIf { it.isNotEmpty() }?.let { "운동 기록" }
        rows += HomeActivityPreviewRow(
            HomeActivityKind.EXERCISE,
            listOfNotNull(strengthSummary, cardioSummary, otherSummary).joinToString(" · ").ifBlank { "운동 기록" }
        )
    }

    if (mealRecords.isNotEmpty()) {
        rows += HomeActivityPreviewRow(HomeActivityKind.MEAL, "${mealRecords.size}회")
    }

    weightRecords.firstNotNullOfOrNull { it.weightKg }?.let { weightKg ->
        rows += HomeActivityPreviewRow(
            HomeActivityKind.WEIGHT,
            MassFormatter.withUnit(weightKg, preferredMassUnit)
        )
    }

    return rows
}

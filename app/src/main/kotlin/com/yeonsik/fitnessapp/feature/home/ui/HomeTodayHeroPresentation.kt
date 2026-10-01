package com.yeonsik.fitnessapp.feature.home.ui

import com.yeonsik.fitness.shared.feature.workout.model.MassUnit
import com.yeonsik.fitnessapp.data.MassFormatter
import com.yeonsik.fitnessapp.feature.home.model.HomeSnapshot

internal data class HomeHeroDomainStatus(
    val key: String,
    val label: String,
    val value: String,
    val detail: String?,
    val recorded: Boolean,
    val accessibilityValue: String
)

internal data class HomeTodayHeroStatus(
    val domains: List<HomeHeroDomainStatus>,
    val showContinue: Boolean
) {
    val completedDomainCount: Int get() = domains.count { it.recorded }
}

/** Formats only the Home projection; raw Workout summaries stay at the feature read boundary. */
internal fun homeTodayHeroStatus(
    snapshot: HomeSnapshot,
    unit: MassUnit = MassUnit.KG
): HomeTodayHeroStatus {
    val workout = snapshot.todayWorkoutStatus
    val completedLines = buildList {
        if (workout.hasCompletedStrength) {
            val labels = workout.muscleLabels
            add(when {
                labels.isEmpty() -> "완료"
                labels.size <= 2 -> labels.joinToString(" · ")
                else -> "${labels.take(2).joinToString(" · ")} 외 ${labels.size - 2}"
            })
        }
        if (workout.hasCompletedCardio) add("유산소 ${workout.cardioDurationSeconds / 60}분")
        if (isEmpty() && workout.hasCompletedWorkout) add("완료")
    }
    val inProgress = snapshot.inProgressSessionId != null
    val workoutValue = if (inProgress) "진행 중" else completedLines.firstOrNull() ?: "아직"
    val workoutDetail = if (inProgress) completedLines.takeIf { it.isNotEmpty() }?.joinToString(" · ")
        else completedLines.getOrNull(1)
    val completedAccessibility = buildList {
        if (workout.hasCompletedStrength) add(workout.muscleLabels.joinToString(" · ").ifBlank { "완료" })
        if (workout.hasCompletedCardio) add("유산소 ${workout.cardioDurationSeconds / 60}분")
        if (isEmpty() && workout.hasCompletedWorkout) add("완료")
    }.joinToString(" · ")
    val workoutAccessibility = when {
        inProgress && completedAccessibility.isNotEmpty() -> "진행 중, 오늘 완료: $completedAccessibility"
        inProgress -> "진행 중"
        completedAccessibility.isNotEmpty() -> completedAccessibility
        else -> "아직"
    }
    val mealCount = snapshot.mealCounts[snapshot.today] ?: 0
    val mealValue = if (mealCount > 0) "${mealCount}회" else "아직"
    val weight = snapshot.todayWeight
    val weightValue = weight?.let { "${MassFormatter.format(it.weightKg, unit)} ${unit.symbol()}" } ?: "아직"
    return HomeTodayHeroStatus(
        domains = listOf(
            HomeHeroDomainStatus("workout", "운동", workoutValue, workoutDetail,
                workout.hasCompletedWorkout, workoutAccessibility),
            HomeHeroDomainStatus("meal", "식단", mealValue, null, mealCount > 0, mealValue),
            HomeHeroDomainStatus("body", "체중", weightValue, null, weight != null, weightValue)
        ),
        showContinue = inProgress
    )
}

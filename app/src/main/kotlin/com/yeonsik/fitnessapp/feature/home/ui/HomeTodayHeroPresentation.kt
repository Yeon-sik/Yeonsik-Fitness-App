package com.yeonsik.fitnessapp.feature.home.ui

import com.yeonsik.fitness.shared.feature.workout.model.MassUnit
import com.yeonsik.fitnessapp.data.MassFormatter
import com.yeonsik.fitnessapp.data.NutritionCalculator
import com.yeonsik.fitnessapp.data.NutritionProfile
import com.yeonsik.fitnessapp.feature.home.model.HomeSnapshot
import java.time.LocalDate
import kotlin.math.abs

internal enum class HomeHeroDetailTone { DEFAULT, INCREASE, DECREASE }

internal data class HomeHeroDomainStatus(
    val key: String,
    val label: String,
    val value: String,
    val detail: String?,
    val recorded: Boolean,
    val accessibilityValue: String,
    val progress: Float = if (recorded) 1f else 0f,
    val completionDescription: String = if (recorded) "기록 완료" else "미기록",
    val mealCount: Int? = null,
    val additionalDetail: String? = null,
    val detailTone: HomeHeroDetailTone = HomeHeroDetailTone.DEFAULT
)

internal data class HomeTodayHeroStatus(
    val domains: List<HomeHeroDomainStatus>,
    val showContinue: Boolean
) {
    val completedDomainCount: Int get() = domains.count { it.recorded }
    val progress: Float get() = domains.map { it.progress }.average().toFloat()
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
    val completedVolume = workout.completedStrengthVolumeKg
        .takeIf { workout.hasCompletedStrength && it.isFinite() && it >= 0.0 }
        ?.let { "${MassFormatter.format(it, unit)} ${unit.symbol()}" }
    val workoutValue = if (inProgress) "진행 중" else completedLines.firstOrNull() ?: "아직"
    val workoutDetail = if (inProgress) completedLines.takeIf { it.isNotEmpty() }?.joinToString(" · ")
        else completedVolume ?: completedLines.getOrNull(1)
    val workoutAdditionalDetail = if (inProgress) completedVolume
        else completedLines.getOrNull(1).takeIf { completedVolume != null }
    val completedAccessibility = buildList {
        if (workout.hasCompletedStrength) add(workout.muscleLabels.joinToString(" · ").ifBlank { "완료" })
        if (completedVolume != null) add("총 운동량 $completedVolume")
        if (workout.hasCompletedCardio) add("유산소 ${workout.cardioDurationSeconds / 60}분")
        if (isEmpty() && workout.hasCompletedWorkout) add("완료")
    }.joinToString(" · ")
    val workoutAccessibility = when {
        inProgress && completedAccessibility.isNotEmpty() -> "진행 중, 오늘 완료: $completedAccessibility"
        inProgress -> "진행 중"
        completedAccessibility.isNotEmpty() -> completedAccessibility
        else -> "아직"
    }
    val weight = snapshot.todayWeight
    val weightValue = weight?.let { "${MassFormatter.format(it.weightKg, unit)} ${unit.symbol()}" } ?: "아직"
    val yesterday = snapshot.yesterdayWeight?.takeIf {
        it.date == LocalDate.parse(snapshot.today).minusDays(1).toString() &&
            it.weightKg.isFinite() && it.weightKg > 0.0
    }
    val delta = weight?.takeIf { it.weightKg.isFinite() && it.weightKg > 0.0 }
        ?.let { current -> yesterday?.let { current.weightKg - it.weightKg } }
    // Use the displayed precision so an unchanged value never appears as +0 or -0.
    val deltaMagnitude = delta?.let { MassFormatter.format(abs(it), unit) }
    val direction = when {
        delta == null || deltaMagnitude == "0" -> HomeHeroDetailTone.DEFAULT
        delta > 0.0 -> HomeHeroDetailTone.INCREASE
        else -> HomeHeroDetailTone.DECREASE
    }
    val weightDetail = when {
        weight == null -> null
        deltaMagnitude == null -> "어제 기록 없음"
        else -> "어제보다 ${when (direction) {
            HomeHeroDetailTone.INCREASE -> "+"
            HomeHeroDetailTone.DECREASE -> "-"
            HomeHeroDetailTone.DEFAULT -> ""
        }}$deltaMagnitude ${unit.symbol()}"
    }
    return HomeTodayHeroStatus(
        domains = listOf(
            HomeHeroDomainStatus("workout", "운동", workoutValue, workoutDetail,
                workout.hasCompletedWorkout, workoutAccessibility, additionalDetail = workoutAdditionalDetail),
            homeMealHeroStatus(snapshot),
            HomeHeroDomainStatus("body", "체중", weightValue, weightDetail, weight != null,
                listOfNotNull(weightValue, weightDetail).joinToString(", "), detailTone = direction)
        ),
        showContinue = inProgress
    )
}

/** Hero measures protein attainment; activity history continues to measure recorded meals. */
private fun homeMealHeroStatus(snapshot: HomeSnapshot): HomeHeroDomainStatus {
    val mealCount = (snapshot.mealCounts[snapshot.today] ?: 0).coerceAtLeast(0)
    val hasMeals = mealCount > 0
    val total = snapshot.mealNutritionTotals[snapshot.today]?.total(NutritionProfile.PROTEIN_GRAMS)
    val protein = if (!hasMeals) 0.0 else total?.takeIf { it.knownCount() > 0 }
        ?.knownSum()?.takeIf { it.isFinite() && it >= 0.0 }
    val goal = snapshot.nutritionGoal?.proteinGrams?.takeIf { it.isFinite() && it > 0.0 }
    val lowerBound = hasMeals && protein != null && total?.isComplete() != true
    val prefix = if (lowerBound) "≥" else ""
    val percent = if (protein != null && goal != null) (protein / goal * 100).toInt() else null
    val current = protein?.let { "${prefix}${NutritionCalculator.trim(it)}g" } ?: "미확인"
    val value = "현재 : ${mealCount}끼, $current" + (percent?.let { " (${prefix}${it}%)" } ?: "")
    val detail = "목표 단백질 : " + (goal?.let { "${NutritionCalculator.trim(it)}g" } ?: "미설정")
    // A known subtotal is a lower bound; it can prove attainment without guessing missing values.
    val complete = protein != null && goal != null && protein >= goal
    val progress = if (protein != null && goal != null) (protein / goal).coerceIn(0.0, 1.0).toFloat() else 0f
    val description = when {
        goal == null -> "단백질 목표 미설정"
        protein == null -> "단백질 섭취량 미확인"
        complete -> "단백질 목표 달성"
        else -> "단백질 목표 달성률 ${prefix}${percent}%"
    }
    return HomeHeroDomainStatus("meal", "식단", value, detail, complete, "$detail, $value",
        progress, description, mealCount)
}

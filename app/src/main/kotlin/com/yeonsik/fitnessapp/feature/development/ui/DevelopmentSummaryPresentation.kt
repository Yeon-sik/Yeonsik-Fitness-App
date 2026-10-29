package com.yeonsik.fitnessapp.feature.development.ui

import com.yeonsik.fitness.shared.feature.workout.model.MassUnit
import com.yeonsik.fitnessapp.data.MassFormatter
import com.yeonsik.fitnessapp.feature.development.model.DevelopmentComparisonMetric
import com.yeonsik.fitnessapp.feature.development.model.DevelopmentComparisonState
import com.yeonsik.fitnessapp.feature.development.model.DevelopmentExerciseComparison
import com.yeonsik.fitnessapp.feature.development.model.DevelopmentSetSuggestion

/** Presentation only: the advisor remains the authority for set counts and comparisons. */
internal fun developmentSetSummary(suggestion: DevelopmentSetSuggestion): String = when {
    suggestion.suggestedSets == null -> "최근 세트 구성 확인"
    suggestion.suggestedSets == suggestion.baselineSets -> "${suggestion.suggestedSets}세트 유지 제안"
    else -> "${suggestion.baselineSets} → ${suggestion.suggestedSets}세트 제안"
}

internal fun developmentComparisonValues(comparison: DevelopmentExerciseComparison, unit: MassUnit): String {
    if (comparison.state == DevelopmentComparisonState.COMPARABLE &&
        comparison.previousValue != null && comparison.currentValue != null && comparison.metric != null) {
        fun value(number: Double): String = when (comparison.metric) {
            DevelopmentComparisonMetric.LOAD -> MassFormatter.withUnit(number, unit)
            DevelopmentComparisonMetric.DURATION -> "${number.toInt()}초"
            else -> "${number.toInt()}회"
        }
        return "이전 ${value(comparison.previousValue)} → 최근 ${value(comparison.currentValue)}"
    }
    val current = comparison.current.normalSets.size
    val previous = comparison.previous?.normalSets?.size
    return if (previous == null) "최근 일반 세트 ${current}개"
        else "일반 세트 · 이전 ${previous}개 / 최근 ${current}개"
}

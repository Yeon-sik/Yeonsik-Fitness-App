package com.yeonsik.fitnessapp.feature.development.model

import com.yeonsik.fitness.shared.feature.workout.model.WorkoutSetCountProposal
import java.time.LocalDate

data class DevelopmentGuidance(
    val referenceDate: LocalDate,
    val training: List<DevelopmentSetSuggestion> = emptyList(),
    val nutrition: List<DevelopmentAdvice> = emptyList(),
    val recovery: DevelopmentAdvice? = null,
    val nutritionError: String? = null,
    val recoveryError: String? = null
)

enum class DevelopmentSetAdjustment { MAINTAIN, CONSIDER_ADD, CONSIDER_REDUCE, REVIEW }
enum class DevelopmentAdviceAction { MEALS, RECOVERY, BODY }

data class DevelopmentSetSuggestion(
    val exerciseKey: String,
    val exerciseName: String,
    val adjustment: DevelopmentSetAdjustment,
    val baselineSets: Int,
    val suggestedSets: Int?,
    val reason: String,
    val evidence: List<String>,
    val proposal: WorkoutSetCountProposal?,
    val priority: Int = 0,
    val ruleVersion: String = "local-sets-v1"
)

data class DevelopmentAdvice(
    val id: String,
    val title: String,
    val summary: String,
    val evidence: List<String>,
    val action: DevelopmentAdviceAction,
    val actionLabel: String,
    val priority: Int = 0
)

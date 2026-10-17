package com.yeonsik.fitnessapp.feature.development.model

import com.yeonsik.fitness.shared.feature.workout.model.WorkoutProgressSession
import com.yeonsik.fitness.shared.feature.workout.model.WorkoutProgressSet
import com.yeonsik.fitness.shared.feature.workout.model.WorkoutSetType
import java.time.LocalDate

data class DevelopmentTrainingOverview(
    val windowStart: LocalDate,
    val windowEnd: LocalDate,
    val exercises: List<DevelopmentExerciseProgress> = emptyList(),
    val bodyParts: List<DevelopmentBodyPartProgress> = emptyList(),
    val completedDates: Set<String> = emptySet(),
    val unlinkedExerciseCount: Int = 0,
    val unlinkedEvidence: WorkoutProgressSession? = null
) {
    companion object {
        fun empty(date: LocalDate) = DevelopmentTrainingOverview(date.minusDays(55), date)
    }
}

enum class DevelopmentComparisonMetric { REPETITIONS, LOAD, DURATION }
enum class DevelopmentComparisonState { FIRST_RECORD, COMPARABLE, DIFFERENT_CONDITIONS, UNAVAILABLE }

data class DevelopmentPerformanceSample(
    val session: WorkoutProgressSession,
    /** Repeating the same variant in one session cannot be matched to a prior occurrence safely. */
    val ambiguousOccurrence: Boolean = false
) {
    val normalSets: List<WorkoutProgressSet> get() = session.sets.filter {
        it.type == WorkoutSetType.NORMAL && it.dropParentSetId == null
    }.sortedBy { it.setIndex }
}

data class DevelopmentExerciseComparison(
    val state: DevelopmentComparisonState,
    val previous: DevelopmentPerformanceSample?,
    val current: DevelopmentPerformanceSample,
    val metric: DevelopmentComparisonMetric? = null,
    val previousValue: Double? = null,
    val currentValue: Double? = null,
    /** Fixed external/added/assistance load when comparing repetitions or time. */
    val referenceLoadKg: Double? = null,
    val reason: String? = null
)

data class DevelopmentExerciseProgress(
    val key: String,
    val name: String,
    val history: List<DevelopmentPerformanceSample>,
    val comparison: DevelopmentExerciseComparison,
    /** Null values are excluded from the series of observations with matching conditions. */
    val trendValues: List<Double?>
)

data class DevelopmentBodyPartProgress(
    val bodyPart: String,
    val currentSets: Int,
    val previousSets: Int,
    val currentDays: Int
)

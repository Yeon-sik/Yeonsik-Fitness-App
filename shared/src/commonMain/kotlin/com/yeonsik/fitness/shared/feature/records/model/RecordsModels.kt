package com.yeonsik.fitness.shared.feature.records.model

import com.yeonsik.fitness.shared.feature.body.model.BodyReadEntry
import com.yeonsik.fitness.shared.feature.meal.model.MealReadSummary

/** A single date in the Records calendar. All flags are derived from owner-scoped source reads. */
data class RecordsCalendarDay(
    val date: String,
    val hasWorkout: Boolean,
    val hasBodyMetric: Boolean,
    val hasMeal: Boolean,
    val muscleLabels: List<String> = emptyList()
) {
    val hasAnyRecord: Boolean
        get() = hasWorkout || hasBodyMetric || hasMeal
}

/** Compact workout fact shown in the selected-day Records detail. */
data class RecordsWorkoutSummary(
    val id: String,
    val date: String,
    val title: String,
    val workoutType: String,
    val durationSeconds: Int,
    val totalVolumeKg: Double,
    val completedSetCount: Int,
    val muscleLabels: List<String>
)

data class RecordsDayDetail(
    val date: String,
    val workouts: List<RecordsWorkoutSummary>,
    val bodyMetrics: List<BodyReadEntry>,
    val meals: List<MealReadSummary>
) {
    val hasAnyRecord: Boolean
        get() = workouts.isNotEmpty() || bodyMetrics.isNotEmpty() || meals.isNotEmpty()
}

/** One daily average. The value stays canonical kg until the UI converts it for display. */
data class RecordsWeightPoint(
    val date: String,
    val averageKg: Double
)

data class RecordsSnapshot(
    val ownerId: String,
    val displayedMonth: String,
    val selectedDate: String,
    val today: String,
    val calendarDays: List<RecordsCalendarDay>,
    val selectedDay: RecordsDayDetail,
    val weightTrend: List<RecordsWeightPoint>
)

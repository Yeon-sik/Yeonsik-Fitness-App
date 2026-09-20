package com.yeonsik.fitnessapp.feature.workout.model

import com.yeonsik.fitness.shared.feature.exercise.model.ExerciseFamilyIdentity
import com.yeonsik.fitness.shared.feature.exercise.model.BodyPart
import com.yeonsik.fitness.shared.feature.exercise.model.EquipmentType
import com.yeonsik.fitness.shared.feature.exercise.model.LoadState
import com.yeonsik.fitness.shared.feature.workout.model.MassUnit

/** Android- and storage-independent values consumed by the workout feature. */
data class WorkoutExercise(
    @JvmField val id: String,
    @JvmField val exerciseId: String,
    @JvmField val orderIndex: Int,
    @JvmField val name: String,
    @JvmField val uiPart: String = "",
    @JvmField val equipment: String = "",
    @JvmField val recordType: String,
    @JvmField val familyIdentity: ExerciseFamilyIdentity? = null
)

/** Workout-owned facts consumed by downstream presentation features. */
data class WorkoutWeekProgress(
    val completedSessions: Int,
    val completedDays: Int
)

data class WorkoutBodyPartSets(
    val uiPart: String?,
    val setCount: Int
)

/** Compact completed-workout facts exposed to cross-feature read compositions. */
data class WorkoutReadSessionSummary(
    val id: String,
    val date: String,
    val title: String,
    val workoutType: String,
    val durationSeconds: Int,
    val totalVolumeKg: Double,
    val completedSetCount: Int,
    val muscleLabels: List<String>
)

/** Workout-owned completed-set facts for cross-feature performance trends. */
data class WorkoutExercisePerformancePoint(
    val performanceKey: String,
    val exerciseName: String,
    val date: String,
    val estimatedOneRepMaxKg: Double?,
    val volumeKg: Double?
)

data class WorkoutSet(
    @JvmField val id: String,
    @JvmField val setIndex: Int,
    @JvmField val weightKg: Double,
    @JvmField val actualReps: Int,
    @JvmField val rir: Int?,
    @JvmField val restSeconds: Int?,
    @JvmField val isCompleted: Boolean,
    @JvmField val durationSeconds: Int,
    @JvmField val distanceMeters: Double,
    @JvmField val assistedWeightKg: Double,
    @JvmField val addedWeightKg: Double,
    @JvmField val loadState: LoadState?,
    @JvmField val inputLoadValue: Double?,
    @JvmField val inputLoadUnit: MassUnit?
)

data class WorkoutExerciseDetail(
    val recordId: String,
    val activeExercise: WorkoutExercise,
    val exercises: List<WorkoutExercise>,
    val sets: List<WorkoutSet>,
    val lastHistory: WorkoutExerciseHistory? = null,
    val bests: List<WorkoutExerciseBests> = emptyList(),
    val recentVolumes: List<WorkoutVolumePoint> = emptyList(),
    val allowedLoadStates: Map<String, List<LoadState>> = emptyMap(),
    val volumeFormula: String = "",
    val volumeBySetId: Map<String, Double> = emptyMap()
)

data class WorkoutSessionSnapshot(
    val recordId: String,
    val title: String,
    val status: String,
    val startedAt: String,
    val durationSeconds: Int,
    val totalVolumeKg: Double,
    val completedSetCount: Int,
    val exercises: List<WorkoutSessionExercise>,
    val recentVolumes: List<WorkoutVolumePoint>,
    /** Stable routine identity captured in the workout record metadata, when available. */
    val routineId: String? = null,
    /** Read-only comparison facts from the previous completed session of the same routine. */
    val previousRoutine: WorkoutRoutineComparison? = null
)

data class WorkoutRoutineComparison(
    val date: String,
    val totalVolumeKg: Double,
    val completedSetCount: Int
)

data class WorkoutSessionExercise(
    val id: String,
    val exerciseId: String,
    val orderIndex: Int,
    val name: String,
    val uiPart: String,
    val equipment: String,
    val recordType: String,
    val recordTypeLabel: String,
    val familyIdentity: ExerciseFamilyIdentity?,
    val completedSetCount: Int,
    val totalSetCount: Int,
    val completedSets: List<WorkoutSet> = emptyList(),
    val primarySubPart: String? = null
)

data class WorkoutVolumePoint(
    val date: String,
    val label: String,
    val volumeKg: Double
)

data class WorkoutExerciseHistory(
    @JvmField val date: String,
    @JvmField val totalVolumeKg: Double,
    @JvmField val sets: List<WorkoutSet>
)

data class WorkoutExerciseBests(
    @JvmField val performanceKey: String,
    @JvmField val loadState: LoadState?,
    @JvmField val maxWeightKg: Double,
    @JvmField val repsAtMaxWeight: Int,
    @JvmField val maxWeightDate: String,
    @JvmField val bestE1rmKg: Double,
    @JvmField val bestE1rmDate: String,
    @JvmField val bestSessionVolumeKg: Double,
    @JvmField val bestVolumeDate: String,
    @JvmField val sessionCount: Int
)

data class WorkoutSetInput(
    val weightKg: Double?,
    val reps: Int?,
    val durationSeconds: Int?,
    val distanceMeters: Double?,
    val assistedWeightKg: Double?,
    val addedWeightKg: Double?,
    val rir: Int?,
    val restSeconds: Int?,
    val completed: Boolean,
    val loadState: LoadState?,
    val inputLoadValue: Double?,
    val inputLoadUnit: MassUnit?
)

data class WorkoutExerciseReplacement(
    val masterExerciseId: String,
    val nameKo: String,
    val nameEn: String?,
    val bodyPart: BodyPart?,
    val equipmentType: EquipmentType?,
    val equipmentVariantId: String?,
    val primarySubPart: String?,
    val recordType: String,
    val familyIdentity: ExerciseFamilyIdentity?
)

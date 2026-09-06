package com.yeonsik.fitnessapp.feature.workout.model

import com.yeonsik.fitnessapp.exercise.ExerciseFamilyIdentity
import com.yeonsik.fitnessapp.exercise.BodyPart
import com.yeonsik.fitnessapp.exercise.EquipmentType
import com.yeonsik.fitnessapp.exercise.LoadState
import com.yeonsik.fitnessapp.data.MassUnit

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
    val bests: WorkoutExerciseBests? = null,
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
    val recentVolumes: List<WorkoutVolumePoint>
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
    val totalSetCount: Int
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

package com.yeonsik.fitnessapp.feature.workout.data

import android.content.Context
import com.yeonsik.fitness.shared.core.account.AccountScope
import com.yeonsik.fitnessapp.core.database.FitnessRoomDatabase
import com.yeonsik.fitnessapp.core.database.RoomTransactionRunner
import com.yeonsik.fitnessapp.data.FitnessRecordContract
import com.yeonsik.fitness.shared.feature.workout.api.WorkoutCompletion
import com.yeonsik.fitness.shared.feature.workout.api.WorkoutRepositoryApi
import com.yeonsik.fitness.shared.feature.workout.model.WorkoutExercise
import com.yeonsik.fitness.shared.feature.workout.model.ManualWorkoutExercise
import com.yeonsik.fitness.shared.feature.workout.model.WorkoutExerciseBests
import com.yeonsik.fitness.shared.feature.workout.model.WorkoutExerciseDetail
import com.yeonsik.fitness.shared.feature.workout.model.WorkoutExerciseHistory
import com.yeonsik.fitness.shared.feature.workout.model.WorkoutExercisePersonalBests
import com.yeonsik.fitness.shared.feature.workout.model.WorkoutExerciseReplacement
import com.yeonsik.fitness.shared.feature.workout.model.WorkoutSessionExercise
import com.yeonsik.fitness.shared.feature.workout.model.WorkoutSessionSnapshot
import com.yeonsik.fitness.shared.feature.workout.model.WorkoutSet
import com.yeonsik.fitness.shared.feature.workout.model.WorkoutSetInput
import com.yeonsik.fitness.shared.feature.workout.model.WorkoutVolumePoint
import com.yeonsik.fitness.shared.feature.routine.model.RoutineExerciseInstance

/** Room-backed workout repository. Legacy Java DTOs do not cross this boundary. */
class WorkoutRepositoryImplementation(
    private val storage: WorkoutRoomStorage
) : WorkoutRepositoryApi {
    constructor(roomDatabase: FitnessRoomDatabase, context: Context) : this(
        WorkoutRoomStorage(roomDatabase, context)
    )

    constructor(
        roomDatabase: FitnessRoomDatabase,
        context: Context,
        transactionRunner: RoomTransactionRunner
    ) : this(WorkoutRoomStorage(roomDatabase, context, transactionRunner))

    override fun latestInProgressSession(scope: AccountScope): String? = storage.latestInProgress(scope)

    override fun lastPerformedAtByCanonicalPreset(scope: AccountScope): Map<String, String> =
        storage.lastPerformedAtByCanonicalPreset(scope)

    override fun createEmptySession(scope: AccountScope, date: String): String =
        storage.createSession(scope, date, "루틴 없이 운동", "strength", "", storage.nowValue(), "")

    override fun createSessionFromRoutine(
        scope: AccountScope,
        date: String,
        title: String,
        routineId: String?,
        exercises: List<RoutineExerciseInstance>
    ): String = storage.createSessionFromRoutine(scope, date, title, routineId, exercises)

    override fun createManualPastSessionFromRoutine(
        scope: AccountScope,
        date: String,
        title: String,
        routineId: String?,
        exercises: List<RoutineExerciseInstance>,
        startedAt: String,
        endedAt: String
    ): String = storage.createManualPastSessionFromRoutine(
        scope, date, title, routineId, exercises, startedAt, endedAt
    )

    override fun deleteSession(scope: AccountScope, recordId: String): Boolean =
        storage.deleteSession(scope, recordId)

    override fun createCardioSession(
        scope: AccountScope,
        date: String,
        activityId: String,
        activityLabel: String
    ): String = storage.createCardioSession(scope, date, activityId, activityLabel)

    override fun completeCardioSession(
        scope: AccountScope,
        recordId: String,
        activityId: String,
        activityLabel: String,
        durationSeconds: Int,
        distanceMeters: Double?,
        averageHeartRateBpm: Int?
    ): Boolean = storage.completeCardioSession(
        scope,
        recordId,
        activityId,
        activityLabel,
        durationSeconds,
        distanceMeters,
        averageHeartRateBpm
    )

    override fun updateCardioAverageHeartRate(
        scope: AccountScope,
        recordId: String,
        averageHeartRateBpm: Int?
    ): Boolean = storage.updateCardioAverageHeartRate(scope, recordId, averageHeartRateBpm)

    override fun loadSession(scope: AccountScope, recordId: String): WorkoutSessionSnapshot? {
        val info = storage.sessionInfo(scope, recordId) ?: return null
        val exercises = storage.exercises(scope, recordId)
        val mappedExercises = exercises.map { exercise ->
            val sets = storage.sets(scope, exercise.id)
            WorkoutSessionExercise(
                exercise.id,
                exercise.exerciseId,
                exercise.orderIndex,
                exercise.name,
                exercise.uiPart,
                exercise.equipment,
                exercise.recordType,
                FitnessRecordContract.displayRecordTypeKo(exercise.recordType),
                exercise.familyIdentity,
                sets.count { it.isCompleted },
                sets.size,
                sets.filter { it.isCompleted }.map { it.toFeatureModel() },
                exercise.primarySubPart
            )
        }
        val metrics = storage.metrics(scope, recordId)
        return WorkoutSessionSnapshot(
            recordId,
            info.title,
            info.status,
            info.startedAt,
            info.durationSeconds,
            metrics.totalVolumeKg,
            metrics.setCount,
            mappedExercises,
            storage.recentSessionVolumes(scope, recordId, 4).map {
                WorkoutVolumePoint(it.date, it.label, it.volumeKg)
            },
            routineId = info.routineId,
            previousRoutine = storage.previousCompletedRoutine(scope, recordId, info.routineId),
            completedAt = info.completedAt.takeIf { it.isNotBlank() }
        )
    }

    override fun loadExerciseDetail(
        scope: AccountScope,
        recordId: String,
        activeExerciseId: String?
    ): WorkoutExerciseDetail? {
        val session = storage.sessionInfo(scope, recordId) ?: return null
        val exercises = storage.exercises(scope, recordId)
        val active = exercises.firstOrNull { it.id == activeExerciseId } ?: exercises.firstOrNull()
            ?: return null
        val activeSets = storage.sets(scope, active.id)
        val completedSets = activeSets.filter { it.isCompleted }
        val currentVolumeKg = completedSets.sumOf { storage.volumeForSet(active, it) }
        val currentOneRepMaxKg = completedSets.mapNotNull {
            storage.estimatedOneRepMaxKg(active, it)
        }.maxOrNull()
        val previousHistories = storage.recentExerciseHistories(
            scope,
            active,
            recordId,
            limit = if (completedSets.isEmpty()) 5 else 4
        )
        val bests = storage.bests(scope, active, recordId)
        val currentIsOneRepMax = currentOneRepMaxKg != null &&
            currentOneRepMaxKg >= (bests.estimatedOneRepMax?.valueKg ?: 0.0)
        val currentIsVolumeBest = currentVolumeKg > 0.0 &&
            currentVolumeKg >= (bests.highestTotalVolume?.valueKg ?: 0.0)
        val currentHistory = completedSets.takeIf { it.isNotEmpty() }?.let { sets ->
            WorkoutExerciseHistory(
                date = session.date,
                totalVolumeKg = currentVolumeKg,
                sets = sets.map { it.toFeatureModel() },
                recordId = recordId,
                estimatedOneRepMaxKg = currentOneRepMaxKg
            )
        }
        val recentHistories = (previousHistories.map { history ->
            WorkoutExerciseHistory(
                date = history.date,
                totalVolumeKg = history.totalVolumeKg,
                sets = history.sets.map { it.toFeatureModel() },
                recordId = history.recordId,
                estimatedOneRepMaxKg = history.estimatedOneRepMaxKg
            )
        } + listOfNotNull(currentHistory)).takeLast(5)
        return WorkoutExerciseDetail(
            recordId = recordId,
            activeExercise = active.toFeatureModel(),
            exercises = exercises.map { it.toFeatureModel() },
            sets = activeSets.map { it.toFeatureModel() },
            lastHistory = previousHistories.lastOrNull()?.let { history ->
                WorkoutExerciseHistory(
                    date = history.date,
                    totalVolumeKg = history.totalVolumeKg,
                    sets = history.sets.map { it.toFeatureModel() },
                    recordId = history.recordId,
                    estimatedOneRepMaxKg = history.estimatedOneRepMaxKg
                )
            },
            bests = bests.byLoadState.map { it.toFeatureModel() },
            recentVolumes = storage.recentExerciseVolumes(scope, active, recordId, 8).map {
                WorkoutVolumePoint(it.date, it.label, it.volumeKg)
            },
            allowedLoadStates = exercises.associate { it.id to storage.allowedLoadStates(it) },
            volumeFormula = storage.volumeFormula(active),
            volumeBySetId = activeSets.associate { it.id to storage.volumeForSet(active, it) },
            recentHistories = recentHistories,
            personalBests = WorkoutExercisePersonalBests(
                estimatedOneRepMaxKg = listOfNotNull(
                    currentOneRepMaxKg,
                    bests.estimatedOneRepMax?.valueKg
                ).maxOrNull(),
                estimatedOneRepMaxDate = if (currentIsOneRepMax) {
                    session.date
                } else bests.estimatedOneRepMax?.date,
                totalVolumeKg = listOfNotNull(
                    currentVolumeKg.takeIf { it > 0.0 },
                    bests.highestTotalVolume?.valueKg
                ).maxOrNull(),
                totalVolumeDate = if (currentIsVolumeBest) {
                    session.date
                } else bests.highestTotalVolume?.date
            ),
            currentRecordDate = session.date
        )
    }

    override fun ensureInitialSet(scope: AccountScope, recordId: String, exerciseId: String): Boolean =
        storage.ensureInitialSet(scope, recordId, exerciseId)

    override fun completeIfEligible(scope: AccountScope, recordId: String): WorkoutCompletion =
        if (storage.complete(scope, recordId)) WorkoutCompletion.COMPLETED
        else WorkoutCompletion.NO_COMPLETED_SETS

    override fun discard(scope: AccountScope, recordId: String) {
        storage.discard(scope, recordId)
    }

    override fun updateTypedSet(
        scope: AccountScope,
        recordId: String,
        setId: String,
        input: WorkoutSetInput
    ): Boolean = storage.updateSet(scope, recordId, setId, input)

    override fun addTypedSet(
        scope: AccountScope,
        recordId: String,
        exerciseId: String,
        setIndex: Int,
        input: WorkoutSetInput
    ): Boolean = storage.addSet(scope, recordId, exerciseId, setIndex, input)

    override fun deleteSet(scope: AccountScope, recordId: String, setId: String): Boolean =
        storage.deleteSet(scope, recordId, setId)

    override fun deleteExercise(scope: AccountScope, recordId: String, exerciseId: String): Boolean =
        storage.deleteExercise(scope, recordId, exerciseId)

    override fun addExercise(scope: AccountScope, recordId: String,
                             exercise: WorkoutExerciseReplacement): Boolean =
        storage.addExercise(scope, recordId, exercise)

    override fun replaceExercise(
        scope: AccountScope,
        recordId: String,
        exerciseId: String,
        replacement: WorkoutExerciseReplacement
    ): Boolean = storage.replaceExercise(scope, recordId, exerciseId, replacement)

    override fun addManualExercise(scope: AccountScope, recordId: String,
                                   exercise: ManualWorkoutExercise): Boolean =
        storage.addManualExercise(scope, recordId, exercise)

    override fun linkManualExerciseToCanonical(scope: AccountScope, recordId: String,
                                              exerciseId: String, canonicalPresetId: String): Boolean =
        storage.linkManualExerciseToCanonical(scope, recordId, exerciseId, canonicalPresetId)

    private fun WorkoutRoomStorage.ExerciseRow.toFeatureModel() = WorkoutExercise(
        id, exerciseId, orderIndex, name, uiPart, equipment, recordType, familyIdentity
    )

    private fun WorkoutRoomStorage.SetRow.toFeatureModel() = WorkoutSet(
        id,
        setIndex,
        weightKg,
        actualReps,
        rir,
        restSeconds,
        isCompleted,
        durationSeconds,
        distanceMeters,
        assistedWeightKg,
        addedWeightKg,
        loadState,
        inputLoadValue,
        inputLoadUnit
    )

    private fun WorkoutRoomStorage.Bests.toFeatureModel() = WorkoutExerciseBests(
        performanceKey,
        loadState,
        maxWeightKg,
        repsAtMaxWeight,
        maxWeightDate,
        bestE1rmKg,
        bestE1rmDate,
        bestSessionVolumeKg,
        bestVolumeDate,
        sessionCount
    )
}

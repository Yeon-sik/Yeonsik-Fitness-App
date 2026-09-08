package com.yeonsik.fitnessapp.feature.workout.data

import android.content.Context
import com.yeonsik.fitnessapp.core.account.AccountScope
import com.yeonsik.fitnessapp.core.database.FitnessRoomDatabase
import com.yeonsik.fitnessapp.core.database.WorkoutExercisesRoomEntity
import com.yeonsik.fitnessapp.core.database.WorkoutRecordsRoomEntity
import com.yeonsik.fitnessapp.core.database.WorkoutRoomDao
import com.yeonsik.fitnessapp.core.database.WorkoutSetsRoomEntity
import com.yeonsik.fitnessapp.data.FitnessRecordContract
import com.yeonsik.fitnessapp.data.MassUnit
import com.yeonsik.fitnessapp.exercise.ExerciseFamilyCatalog
import com.yeonsik.fitnessapp.exercise.ExerciseFamilyIdentity
import com.yeonsik.fitnessapp.exercise.ExerciseVolumeCalculator
import com.yeonsik.fitnessapp.exercise.LoadState
import com.yeonsik.fitnessapp.exercise.RoutineExercise
import com.yeonsik.fitnessapp.feature.workout.model.WorkoutExerciseReplacement
import com.yeonsik.fitnessapp.feature.workout.model.WorkoutSetInput
import com.yeonsik.fitnessapp.feature.routine.model.RoutineExerciseInstance
import org.json.JSONArray
import org.json.JSONObject
import java.time.Duration
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Workout-owned storage boundary.
 *
 * The Room database remains the only connection owner.  This class intentionally exposes
 * storage-neutral workout rows to [WorkoutRepositoryImplementation] and does not depend on the
 * legacy Java repository DTOs.
 */
class WorkoutRoomStorage(
    private val roomDatabase: FitnessRoomDatabase,
    context: Context
) {
    private val familyCatalog = ExerciseFamilyCatalog.load(context)

    data class SessionInfo(
        val title: String,
        val date: String,
        val startedAt: String,
        val status: String,
        val durationSeconds: Int,
        val workoutType: String
    )

    data class ExerciseRow(
        val id: String,
        val exerciseId: String,
        val orderIndex: Int,
        val name: String,
        val uiPart: String,
        val equipment: String,
        val recordType: String,
        val familyIdentity: ExerciseFamilyIdentity?
    )

    data class SetRow(
        val id: String,
        val setIndex: Int,
        val weightKg: Double,
        val actualReps: Int,
        val rir: Int?,
        val restSeconds: Int?,
        val isCompleted: Boolean,
        val durationSeconds: Int,
        val distanceMeters: Double,
        val assistedWeightKg: Double,
        val addedWeightKg: Double,
        val storedVolumeKg: Double,
        val loadState: LoadState?,
        val inputLoadValue: Double?,
        val inputLoadUnit: MassUnit?
    )

    data class Metrics(val totalVolumeKg: Double, val setCount: Int, val totalDistanceMeters: Double)
    data class VolumePoint(val date: String, val label: String, val volumeKg: Double)
    data class History(val date: String, val totalVolumeKg: Double, val sets: List<SetRow>)
    data class Bests(
        val performanceKey: String,
        val loadState: LoadState?,
        val maxWeightKg: Double,
        val repsAtMaxWeight: Int,
        val maxWeightDate: String,
        val bestE1rmKg: Double,
        val bestE1rmDate: String,
        val bestSessionVolumeKg: Double,
        val bestVolumeDate: String,
        val sessionCount: Int
    )

    data class DayMetrics(
        val sessionCount: Int,
        val totalSetCount: Int,
        val totalVolumeKg: Double,
        val totalDurationSeconds: Int
    )

    private val workoutDao: WorkoutRoomDao = roomDatabase.workoutRoomDao()

    fun nowValue(): String = now()

    fun createSession(
        scope: AccountScope,
        date: String,
        title: String,
        sessionType: String,
        memo: String,
        startedAt: String,
        endedAt: String
    ): String {
        require(scope.ownerId.isNotBlank()) { "Workout owner is required." }
        val id = UUID.randomUUID().toString()
        val createdAt = now()
        workoutDao.insertRecord(WorkoutRecordsRoomEntity(
            id,
            scope.ownerId,
            date.ifBlank { java.time.LocalDate.now().toString() },
            sessionType.ifBlank { "other" },
            sessionType.ifBlank { "other" },
            title.ifBlank { "Workout" },
            null,
            0.0,
            null,
            createdAt,
            0L,
            null,
            null,
            createdAt,
            null,
            "android-local",
            "fitness",
            "fitness",
            JSONObject().apply {
                put("contract_version", FitnessRecordContract.VERSION)
                put("status", if (endedAt.isBlank()) "in_progress" else "completed")
                put("started_at", startedAt)
                if (endedAt.isBlank()) put("ended_at", JSONObject.NULL) else put("ended_at", endedAt)
                put("memo", memo)
            }.toString(),
            FitnessRecordContract.VERSION.toLong()
        ))
        return id
    }

    fun createSessionFromRoutine(
        scope: AccountScope,
        date: String,
        title: String,
        routineId: String?,
        routineExercises: List<RoutineExerciseInstance>
    ): String {
        val recordId = createSession(scope, date, title, "strength", "", now(), "")
        populateSessionFromRoutine(scope, recordId, routineId, routineExercises)
        return recordId
    }

    fun createManualPastSessionFromRoutine(
        scope: AccountScope,
        date: String,
        title: String,
        routineId: String?,
        routineExercises: List<RoutineExerciseInstance>,
        startedAt: String,
        endedAt: String
    ): String {
        val recordId = createSession(scope, date, title, "strength", "", startedAt, "")
        val duration = try {
            Duration.between(OffsetDateTime.parse(startedAt), OffsetDateTime.parse(endedAt))
                .seconds.toInt().takeIf { it > 0 }
        } catch (_: Exception) { null }
        require(duration != null) { "운동 시작 시각과 운동 시간을 확인하세요." }
        workoutDao.updateManualPastSession(
            recordId,
            scope.ownerId,
            duration,
            now(),
            JSONObject(sessionInfoMetadata(scope, recordId).ifBlank { "{}" }).apply {
                put("status", "in_progress")
                put("started_at", startedAt)
                put("ended_at", endedAt)
                put("duration_seconds", duration)
                put("total_volume_kg", 0.0)
                put("contract_version", FitnessRecordContract.VERSION)
            }.toString()
        )
        populateSessionFromRoutine(scope, recordId, routineId, routineExercises)
        return recordId
    }

    private fun populateSessionFromRoutine(
        scope: AccountScope,
        recordId: String,
        routineId: String?,
        routineExercises: List<RoutineExerciseInstance>
    ) {
        if (!routineId.isNullOrBlank()) {
            val metadata = sessionInfoMetadata(scope, recordId)
            workoutDao.updateRecordMetadata(
                recordId,
                scope.ownerId,
                JSONObject(metadata.ifBlank { "{}" }).put("routine_id", routineId).toString()
            )
        }
        routineExercises.forEach { exercise ->
            val identity = exercise.familyIdentity ?: familyCatalog.identityForStorageExerciseId(exercise.exerciseId)
            val id = UUID.randomUUID().toString()
            val createdAt = now()
            workoutDao.insertExercise(WorkoutExercisesRoomEntity(
                id,
                scope.ownerId,
                recordId,
                exercise.order.toLong(),
                exercise.exerciseId.ifBlank { "manual" },
                canonicalName(exercise.nameKo, identity),
                exercise.uiPart.ifBlank { "other" },
                exercise.primarySubPart,
                exercise.equipment.takeIf { it.isNotBlank() },
                FitnessRecordContract.normalizeRecordType(exercise.recordType),
                identity?.familyId,
                identity?.presetId,
                identity?.canonicalVariantKey,
                identity?.visualVariantKey,
                null,
                createdAt,
                createdAt,
                null,
                "android-local",
                FitnessRecordContract.VERSION.toLong()
            ))
        }
    }

    private fun sessionInfoMetadata(scope: AccountScope, recordId: String): String =
        workoutDao.visibleRecord(recordId, scope.ownerId)?.metadata.orEmpty().ifBlank { "{}" }

    fun deleteSession(scope: AccountScope, recordId: String): Boolean {
        val timestamp = now()
        var updated = 0
        roomDatabase.runInTransaction {
            workoutDao.visibleExercises(recordId, scope.ownerId).forEach { exercise ->
                workoutDao.tombstoneSetsForExercise(exercise.id, scope.ownerId, timestamp, timestamp)
            }
            workoutDao.tombstoneExercisesForRecord(recordId, scope.ownerId, timestamp, timestamp)
            updated = workoutDao.tombstoneRecord(recordId, scope.ownerId, timestamp, timestamp)
        }
        return updated > 0
    }

    fun sessionInfo(scope: AccountScope, recordId: String): SessionInfo? {
        val record = workoutDao.visibleRecord(recordId, scope.ownerId) ?: return null
        val metadata = record.metadata
        return SessionInfo(
                record.exerciseName,
                record.date,
                metadataValue(metadata, "started_at"),
                metadataValue(metadata, "status"),
                resolvedDurationSeconds(
                    record.date,
                    record.durationSeconds?.toInt(),
                    metadata
                ),
                record.workoutType
            )
    }

    fun exercises(scope: AccountScope, recordId: String): List<ExerciseRow> {
        val result = mutableListOf<ExerciseRow>()
        for (row in workoutDao.visibleExercises(recordId, scope.ownerId)) {
                val exerciseId = row.exerciseId
                val identity = identityForRow(
                    exerciseId,
                    row.exerciseNameSnapshot,
                    row.familyId,
                    row.presetId,
                    row.canonicalVariantKey,
                    row.visualVariantKey
                )
                result += ExerciseRow(
                    row.id,
                    exerciseId,
                    row.orderIndex.toInt(),
                    canonicalName(row.exerciseNameSnapshot, identity),
                    row.uiPart,
                    row.equipmentSnapshot.orEmpty(),
                    FitnessRecordContract.normalizeRecordType(row.recordType),
                    identity
                )
        }
        return result
    }

    fun sets(scope: AccountScope, exerciseId: String): List<SetRow> {
        val exercise = exerciseById(scope, exerciseId)
        val result = mutableListOf<SetRow>()
        for (row in workoutDao.visibleSets(exerciseId, scope.ownerId)) {
                val rawInput = row.inputLoadValue
                val rawUnit = row.inputLoadUnit?.let(MassUnit::parse)
                val validInput = rawInput?.takeIf { it.isFinite() && it >= 0.0 }?.let { value ->
                    if (rawUnit == null) null else value to rawUnit
                }
                result += SetRow(
                    row.id, row.setIndex.toInt(), row.weightKg ?: 0.0, row.actualReps?.toInt() ?: 0,
                    row.rir?.toInt(), row.restSeconds?.toInt(), row.isCompleted == 1L,
                    row.durationSeconds?.toInt() ?: 0, row.distanceMeters ?: 0.0,
                    row.assistedWeightKg ?: 0.0, row.addedWeightKg ?: 0.0, row.volumeKg ?: 0.0,
                    loadStateForRead(exercise?.recordType, exercise?.familyIdentity,
                        row.loadState, row.addedWeightKg ?: 0.0),
                    validInput?.first, validInput?.second
                )
        }
        return result
    }

    fun metrics(scope: AccountScope, recordId: String): Metrics {
        val exerciseById = exercises(scope, recordId).associateBy { it.id }
        var volume = 0.0
        var count = 0
        var distance = 0.0
        for (exercise in exerciseById.values) {
            for (set in sets(scope, exercise.id)) {
                if (!set.isCompleted) continue
                count++
                distance += set.distanceMeters
                volume += volumeForSet(exercise, set)
            }
        }
        return Metrics(volume, count, distance)
    }

    fun dayMetrics(scope: AccountScope, date: String): DayMetrics {
        var sessions = 0
        var sets = 0
        var volume = 0.0
        var duration = 0
        for (record in workoutDao.visibleRecordsForDate(scope.ownerId, date)) {
                sessions++
                val metrics = metrics(scope, record.id)
                sets += metrics.setCount
                volume += metrics.totalVolumeKg
                duration += resolvedDurationSeconds(
                    record.date,
                    record.durationSeconds?.toInt(),
                    record.metadata
                )
        }
        return DayMetrics(sessions, sets, volume, duration)
    }

    fun sessionsForDate(scope: AccountScope, date: String): List<String> {
        return workoutDao.visibleRecordsForDate(scope.ownerId, date)
            .sortedByDescending { it.updatedAt }
            .map { it.id }
    }

    fun latestInProgress(scope: AccountScope): String? {
        return workoutDao.recentVisibleRecords(scope.ownerId, 20)
            .firstOrNull { metadataValue(it.metadata, "status") == "in_progress" }?.id
    }

    fun latestCompletedForRoutine(scope: AccountScope, routineId: String, routineName: String): String? {
        return workoutDao.strengthRecords(scope.ownerId).firstOrNull { record ->
            val metadata = record.metadata
            metadataValue(metadata, "status") == "completed" &&
                (metadataValue(metadata, "routine_id") == routineId ||
                    metadataValue(metadata, "routine_name") == routineName)
        }?.date
    }

    fun recentSessionVolumes(scope: AccountScope, currentRecordId: String, limit: Int): List<VolumePoint> {
        if (limit <= 0) return emptyList()
        val points = mutableListOf<VolumePoint>()
        for (record in workoutDao.recentRecordsExcept(scope.ownerId, currentRecordId, limit)) {
                if (metadataValue(record.metadata, "status") != "completed" && record.sourceApp != "os") continue
                points += VolumePoint(
                    record.date,
                    record.exerciseName,
                    metrics(scope, record.id).totalVolumeKg
                )
        }
        return points.asReversed()
    }

    fun recentExerciseVolumes(
        scope: AccountScope,
        exercise: ExerciseRow,
        currentRecordId: String,
        limit: Int
    ): List<VolumePoint> {
        if (limit <= 0) return emptyList()
        val points = linkedMapOf<String, VolumePoint>()
        for (candidate in workoutDao.exerciseHistoryCandidates(
            scope.ownerId, currentRecordId, exercise.exerciseId, exercise.name
        )) {
            if (points.size >= limit) break
                val recordId = candidate.recordId
                val matchingExercise = exercises(scope, recordId).firstOrNull {
                    it.exerciseId == exercise.exerciseId ||
                        (it.exerciseId == "manual" && it.name == exercise.name)
                } ?: continue
                val volume = sets(scope, matchingExercise.id)
                    .filter { it.isCompleted }
                    .sumOf { volumeForSet(matchingExercise, it) }
                points[recordId] = VolumePoint(candidate.date, candidate.exerciseName, volume)
        }
        return points.values.toList().asReversed()
    }

    fun lastExerciseHistory(scope: AccountScope, exercise: ExerciseRow, currentRecordId: String): History? {
        val row = workoutDao.lastExerciseCandidate(
            scope.ownerId, currentRecordId, exercise.exerciseId, exercise.name
        ) ?: return null
        val sets = sets(scope, row.recordId).filter { it.isCompleted }
        if (sets.isEmpty()) return null
        return History(row.date, sets.sumOf { volumeForSet(exercise, it) }, sets)
    }

    fun bests(scope: AccountScope, exercise: ExerciseRow, currentRecordId: String): Bests {
        var maxWeight = 0.0
        var repsAtMax = 0
        var maxDate = ""
        var bestVolume = 0.0
        var bestVolumeDate = ""
        val sessionVolumes = linkedMapOf<String, Double>()
        val sessionDates = linkedMapOf<String, String>()
        for (row in workoutDao.bestSetRows(
            scope.ownerId, currentRecordId, exercise.exerciseId, exercise.name
        )) {
                val recordId = row.recordId
                val date = row.date
                val weight = row.weightKg ?: 0.0
                val reps = row.actualReps?.toInt() ?: 0
                val set = SetRow("", 0, weight, reps, null, null, true, 0, 0.0,
                    row.assistedWeightKg ?: 0.0, row.addedWeightKg ?: 0.0, 0.0,
                    loadStateForRead(exercise.recordType, exercise.familyIdentity,
                        row.loadState, row.addedWeightKg ?: 0.0),
                    null, null)
                val setVolume = volumeForSet(exercise, set)
                sessionVolumes[recordId] = (sessionVolumes[recordId] ?: 0.0) + setVolume
                sessionDates[recordId] = date
                val comparableLoad = if (set.loadState == LoadState.ADDED_WEIGHT) set.addedWeightKg else set.weightKg
                if (comparableLoad > maxWeight) {
                    maxWeight = comparableLoad
                    repsAtMax = reps
                    maxDate = date
                }
        }
        sessionVolumes.forEach { (recordId, value) ->
            if (value > bestVolume) {
                bestVolume = value
                bestVolumeDate = sessionDates[recordId].orEmpty()
            }
        }
        val e1rm = if (maxWeight > 0 && repsAtMax > 0) maxWeight * (1 + repsAtMax / 30.0) else 0.0
        return Bests(
            "${exercise.exerciseId}:${exercise.recordType}",
            exercise.familyIdentity?.defaultLoadStateValue(),
            maxWeight,
            repsAtMax,
            maxDate,
            e1rm,
            maxDate,
            bestVolume,
            bestVolumeDate,
            sessionVolumes.size
        )
    }

    fun allowedLoadStates(exercise: ExerciseRow): List<LoadState> {
        val default = exercise.familyIdentity?.defaultLoadStateValue()
        return if (default == null) emptyList() else listOf(default)
    }

    fun volumeForSet(exercise: ExerciseRow, set: SetRow): Double {
        val identity = exercise.familyIdentity
        if (identity == null) return set.storedVolumeKg
        return ExerciseVolumeCalculator.calculate(
            exercise.recordType,
            set.loadState,
            set.weightKg,
            set.addedWeightKg,
            set.actualReps,
            laterality(identity),
            implementMultiplier(identity)
        )
    }

    fun volumeFormula(exercise: ExerciseRow): String = ExerciseVolumeCalculator.formulaLabel(
        exercise.familyIdentity?.let(::laterality),
        exercise.familyIdentity?.let(::implementMultiplier) ?: 1
    )

    fun ensureInitialSet(scope: AccountScope, recordId: String, exerciseId: String): Boolean {
        if (sets(scope, exerciseId).isNotEmpty()) return false
        addSet(scope, recordId, exerciseId, 1, WorkoutSetInput(
            null, null, null, null, null, null, null, null, false, null, null, null
        ))
        return true
    }

    fun addSet(scope: AccountScope, recordId: String, exerciseId: String, setIndex: Int, input: WorkoutSetInput): Boolean {
        requireOwnedExercise(scope, recordId, exerciseId)
        val exercise = exerciseById(scope, exerciseId) ?: return false
        val state = input.loadState ?: defaultLoadState(exercise.recordType, input)
        val now = now()
        workoutDao.insertSet(inputEntity(
            UUID.randomUUID().toString(), scope.ownerId, exerciseId, maxOf(1, setIndex), exercise, input, state, now
        ))
        refreshRecordTotal(scope, recordId)
        return true
    }

    fun updateSet(scope: AccountScope, recordId: String, setId: String, input: WorkoutSetInput): Boolean {
        val exercise = exerciseForSet(scope, setId) ?: return false
        val state = input.loadState ?: defaultLoadState(exercise.recordType, input)
        val updated = workoutDao.updateSet(
            setId, scope.ownerId, recordId, input.reps, input.reps,
            weightKg(input, state), input.durationSeconds, input.distanceMeters, input.restSeconds,
            assistedWeightKg(input, state), addedWeightKg(input, state), state?.id(),
            input.inputLoadValue, input.inputLoadUnit?.id(), if (input.completed) 1 else 0,
            volumeForInput(exercise, input, state), input.rir, now()
        )
        if (updated > 0) refreshRecordTotal(scope, recordId)
        return updated > 0
    }

    fun deleteSet(scope: AccountScope, recordId: String, setId: String): Boolean {
        val timestamp = now()
        val updated = workoutDao.tombstoneSet(setId, scope.ownerId, recordId, timestamp, timestamp)
        if (updated > 0) refreshRecordTotal(scope, recordId)
        return updated > 0
    }

    fun deleteExercise(scope: AccountScope, recordId: String, exerciseId: String): Boolean {
        val timestamp = now()
        workoutDao.tombstoneSetsForExercise(exerciseId, scope.ownerId, timestamp, timestamp)
        val updated = workoutDao.tombstoneExercise(exerciseId, recordId, scope.ownerId, timestamp, timestamp)
        if (updated > 0) refreshRecordTotal(scope, recordId)
        return updated > 0
    }

    fun addExercise(scope: AccountScope, recordId: String,
                    exercise: WorkoutExerciseReplacement): Boolean {
        check(workoutDao.ownsRecord(recordId, scope.ownerId) != null) {
            "Workout record is not owned by the requested account."
        }
        val identity = exercise.familyIdentity
            ?: familyCatalog.identityForStorageExerciseId(exercise.masterExerciseId)
        val timestamp = now()
        val order = workoutDao.nextExerciseOrder(recordId, scope.ownerId)
        workoutDao.insertExercise(WorkoutExercisesRoomEntity(
            UUID.randomUUID().toString(), scope.ownerId, recordId, order.toLong(),
            exercise.masterExerciseId.ifBlank { "manual" }, canonicalName(exercise.nameKo, identity),
            exercise.bodyPart?.labelKo() ?: identity?.defaultUiPart.orEmpty(),
            exercise.primarySubPart.orEmpty(), exercise.equipmentType?.labelKo(),
            FitnessRecordContract.normalizeRecordType(exercise.recordType), identity?.familyId,
            identity?.presetId, identity?.canonicalVariantKey, identity?.visualVariantKey, null,
            timestamp, timestamp, null, "android-local", FitnessRecordContract.VERSION.toLong()
        ))
        return true
    }
    fun replaceExercise(scope: AccountScope, recordId: String, exerciseId: String, replacement: WorkoutExerciseReplacement): Boolean {
        val updated = workoutDao.replaceExercise(
            exerciseId, recordId, scope.ownerId,
            replacement.masterExerciseId.ifBlank { "manual" }, replacement.nameKo,
            replacement.bodyPart?.labelKo() ?: "other", replacement.primarySubPart.orEmpty(),
            replacement.equipmentType?.labelKo(), FitnessRecordContract.normalizeRecordType(replacement.recordType),
            replacement.familyIdentity?.familyId, replacement.familyIdentity?.presetId,
            replacement.familyIdentity?.canonicalVariantKey, replacement.familyIdentity?.visualVariantKey, now()
        )
        if (updated > 0) refreshRecordTotal(scope, recordId)
        return updated > 0
    }

    fun complete(scope: AccountScope, recordId: String): Boolean {
        val info = sessionInfo(scope, recordId) ?: return false
        val metrics = metrics(scope, recordId)
        if (metrics.setCount == 0) return false
        val endedAt = now()
        val record = workoutDao.visibleRecord(recordId, scope.ownerId) ?: return false
        val metadata = record.metadata
        val category = categoryFor(scope, recordId)
        val merged = try {
            JSONObject(metadata ?: "{}")
                .put("status", "completed")
                .put("ended_at", endedAt)
                .put("contract_version", FitnessRecordContract.VERSION)
                .put("os_categories", JSONArray().put(category))
                .put("category_codes", FitnessRecordContract.categoryCodes(listOf(category)))
                .toString()
        } catch (_: Exception) { metadata }
        val storedDuration = record.durationSeconds?.toInt() ?: 0
        val duration = if (record.isBackfilled == 1L && storedDuration > 0) storedDuration
                else maxOf(info.durationSeconds, elapsedSeconds(info.startedAt))
        val sourceApp = record.sourceApp
        val nextScope = if (sourceApp == "fitness") "both" else record.scope
        val nextCategory = if (sourceApp == "fitness") category else record.category
        return workoutDao.completeRecord(
            recordId, scope.ownerId, merged, duration, metrics.totalVolumeKg,
            endedAt, nextCategory, nextScope
        ) > 0
    }

    fun discard(scope: AccountScope, recordId: String): Boolean {
        val timestamp = now()
        return workoutDao.tombstoneRecord(recordId, scope.ownerId, timestamp, timestamp) > 0
    }

    private fun exerciseById(scope: AccountScope, id: String): ExerciseRow? =
        workoutDao.visibleExercise(id, scope.ownerId)?.let { row ->
            exercises(scope, row.recordId).firstOrNull { it.id == id }
        }

    private fun exerciseForSet(scope: AccountScope, setId: String): ExerciseRow? =
        workoutDao.visibleSet(setId, scope.ownerId)?.let { exerciseById(scope, it.workoutExerciseId) }

    private fun requireOwnedExercise(scope: AccountScope, recordId: String, exerciseId: String) {
        check(workoutDao.ownsExercise(exerciseId, recordId, scope.ownerId) != null) {
            "Workout exercise is not owned by the requested account."
        }
    }

    private fun refreshRecordTotal(scope: AccountScope, recordId: String) {
        workoutDao.updateRecordTotal(recordId, scope.ownerId, metrics(scope, recordId).totalVolumeKg, now())
    }

    private fun categoryFor(scope: AccountScope, recordId: String): String {
        val values = mutableListOf<String>()
        for (uiPart in workoutDao.visibleUiParts(recordId, scope.ownerId)) {
                val value = when (uiPart.lowercase()) {
                    "chest", "가슴", "가슴운동" -> "가슴"
                    "back", "등", "등운동" -> "등"
                    "legs", "하체", "하체운동" -> "하체"
                    "shoulders", "어깨", "어깨운동" -> "어깨"
                    "abs", "복부", "복근", "복부운동" -> "복부"
                    "triceps", "삼두", "삼두운동" -> "삼두"
                    "biceps", "이두", "이두운동" -> "이두"
                    else -> ""
                }
                if (value.isNotEmpty() && value !in values) values += value
        }
        return values.firstOrNull() ?: "기타"
    }

    private fun volumeForInput(exercise: ExerciseRow, input: WorkoutSetInput, state: LoadState?): Double {
        return ExerciseVolumeCalculator.calculate(
            exercise.recordType,
            state,
            input.weightKg ?: 0.0,
            input.addedWeightKg ?: 0.0,
            input.reps ?: 0,
            exercise.familyIdentity?.let(::laterality),
            exercise.familyIdentity?.let(::implementMultiplier) ?: 1
        )
    }

    private fun inputEntity(
        id: String,
        userId: String,
        exerciseId: String,
        setIndex: Int,
        exercise: ExerciseRow,
        input: WorkoutSetInput,
        state: LoadState?,
        timestamp: String
    ): WorkoutSetsRoomEntity {
        return WorkoutSetsRoomEntity(
            id,
            userId,
            exerciseId,
            setIndex.toLong(),
            input.reps?.toLong(),
            input.reps?.toLong(),
            weightKg(input, state),
            volumeForInput(exercise, input, state),
            input.durationSeconds?.toLong(),
            input.distanceMeters,
            input.restSeconds?.toLong(),
            assistedWeightKg(input, state),
            addedWeightKg(input, state),
            input.inputLoadValue,
            input.inputLoadUnit?.id(),
            state?.id(),
            if (input.completed) 1L else 0L,
            null,
            input.rir?.toLong(),
            null,
            timestamp,
            timestamp,
            null,
            "android-local",
            FitnessRecordContract.VERSION.toLong()
        )
    }

    private fun weightKg(input: WorkoutSetInput, state: LoadState?): Double? = when (state) {
        LoadState.EXTERNAL_LOAD, LoadState.BAND_RESISTED -> input.weightKg
        LoadState.ADDED_WEIGHT, LoadState.ASSISTED, LoadState.BAND_ASSISTED -> null
        else -> input.weightKg
    }

    private fun assistedWeightKg(input: WorkoutSetInput, state: LoadState?): Double? = when (state) {
        LoadState.ASSISTED, LoadState.BAND_ASSISTED -> input.assistedWeightKg
        LoadState.EXTERNAL_LOAD, LoadState.ADDED_WEIGHT, LoadState.BAND_RESISTED -> null
        else -> input.assistedWeightKg
    }

    private fun addedWeightKg(input: WorkoutSetInput, state: LoadState?): Double? = when (state) {
        LoadState.ADDED_WEIGHT -> input.addedWeightKg
        LoadState.EXTERNAL_LOAD, LoadState.ASSISTED, LoadState.BAND_ASSISTED, LoadState.BAND_RESISTED -> null
        else -> input.addedWeightKg
    }

    private fun laterality(identity: ExerciseFamilyIdentity): String? {
        val preset = familyCatalog.runtimeCatalog().presetForStorageExerciseId(identity.legacyExerciseId)
        if (preset != null) return preset.laterality()
        return try { JSONObject(identity.variantJson ?: "{}").optString("laterality", "").takeIf { it.isNotBlank() } } catch (_: Exception) { null }
    }

    private fun implementMultiplier(identity: ExerciseFamilyIdentity): Int =
        familyCatalog.runtimeCatalog().presetForStorageExerciseId(identity.legacyExerciseId)?.implementMultiplier ?: 1

    private fun defaultLoadState(recordType: String, input: WorkoutSetInput): LoadState? = when (FitnessRecordContract.normalizeRecordType(recordType)) {
        FitnessRecordContract.BODYWEIGHT_ADDED_WEIGHT_REPS -> if ((input.addedWeightKg ?: 0.0) > 0) LoadState.ADDED_WEIGHT else LoadState.BODYWEIGHT
        FitnessRecordContract.WEIGHT_REPS -> LoadState.EXTERNAL_LOAD
        else -> null
    }

    private fun identityForRow(exerciseId: String, name: String?, familyId: String?, presetId: String?, canonical: String?, visual: String?): ExerciseFamilyIdentity? {
        return familyCatalog.identityForStorageExerciseId(exerciseId)
            ?: if (!familyId.isNullOrBlank()) familyCatalog.identityForStorageExerciseId(presetId.orEmpty()) else null
    }

    private fun loadStateForRead(recordType: String?, identity: ExerciseFamilyIdentity?, raw: String?, addedWeight: Double): LoadState? =
        LoadState.fromId(raw) ?: identity?.defaultLoadStateValue() ?: when (FitnessRecordContract.normalizeRecordType(recordType)) {
            FitnessRecordContract.BODYWEIGHT_ADDED_WEIGHT_REPS -> if (addedWeight > 0) LoadState.ADDED_WEIGHT else LoadState.BODYWEIGHT
            FitnessRecordContract.WEIGHT_REPS -> LoadState.EXTERNAL_LOAD
            else -> null
        }

    private fun canonicalName(name: String?, identity: ExerciseFamilyIdentity?): String = identity?.displayName()?.takeIf { it.isNotBlank() } ?: name.orEmpty()

    private fun metadataValue(metadata: String?, key: String): String = try { JSONObject(metadata ?: "{}").optString(key, "") } catch (_: Exception) { "" }

    private fun resolvedDurationSeconds(date: String?, duration: Int?, metadata: String): Int {
        if (duration != null && duration > 0) return duration
        return elapsedSeconds(metadataValue(metadata, "started_at"))
    }

    private fun elapsedSeconds(startedAt: String): Int = try {
        val value = Duration.between(OffsetDateTime.parse(startedAt), OffsetDateTime.now()).seconds
        value.coerceAtLeast(0).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    } catch (_: Exception) { 0 }

    private fun now(): String = OffsetDateTime.now().toString()
}

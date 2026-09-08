package com.yeonsik.fitnessapp.feature.workout.data

import android.content.ContentValues
import android.content.Context
import androidx.room.RoomDatabase
import com.yeonsik.fitnessapp.core.account.AccountScope
import com.yeonsik.fitnessapp.core.database.FitnessRoomDatabase
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

    private val database: androidx.sqlite.db.SupportSQLiteDatabase
        get() = roomDatabase.openHelper.writableDatabase

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
        val values = ContentValues().apply {
            put("id", id)
            put("user_id", scope.ownerId)
            put("date", date.ifBlank { java.time.LocalDate.now().toString() })
            put("workout_type", sessionType.ifBlank { "other" })
            put("category", sessionType.ifBlank { "other" })
            put("exercise_name", title.ifBlank { "Workout" })
            put("total_volume_kg", 0.0)
            putNull("duration_seconds")
            putNull("average_heart_rate")
            put("is_backfilled", 0)
            putNull("backfilled_at")
            putNull("backfill_reason")
            put("source_app", "fitness")
            put("scope", "fitness")
            put("metadata", JSONObject().apply {
                put("contract_version", FitnessRecordContract.VERSION)
                put("status", if (endedAt.isBlank()) "in_progress" else "completed")
                put("started_at", startedAt)
                if (endedAt.isBlank()) put("ended_at", JSONObject.NULL) else put("ended_at", endedAt)
                put("memo", memo)
            }.toString())
            put("created_at", createdAt)
            put("updated_at", createdAt)
            putNull("deleted_at")
            put("device_id", "android-local")
            put("contract_version", FitnessRecordContract.VERSION)
        }
        check(database.insert("workout_records", 0, values) != -1L) { "운동 기록을 저장하지 못했습니다." }
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
        database.update("workout_records", 0, ContentValues().apply {
            put("duration_seconds", duration)
            put("is_backfilled", 1)
            put("backfilled_at", now())
            put("backfill_reason", "manual_entry")
            put("metadata", JSONObject(sessionInfoMetadata(scope, recordId).ifBlank { "{}" }).apply {
                put("status", "in_progress")
                put("started_at", startedAt)
                put("ended_at", endedAt)
                put("duration_seconds", duration)
                put("total_volume_kg", 0.0)
                put("contract_version", FitnessRecordContract.VERSION)
            }.toString())
        }, "id = ? AND user_id = ?", arrayOf(recordId, scope.ownerId))
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
            database.update("workout_records", 0, ContentValues().apply {
                put("metadata", JSONObject(metadata.ifBlank { "{}" }).put("routine_id", routineId).toString())
            }, "id = ? AND user_id = ?", arrayOf(recordId, scope.ownerId))
        }
        routineExercises.forEach { exercise ->
            val identity = exercise.familyIdentity ?: familyCatalog.identityForStorageExerciseId(exercise.exerciseId)
            val id = UUID.randomUUID().toString()
            val createdAt = now()
            check(database.insert("workout_exercises", 0, ContentValues().apply {
                put("id", id)
                put("user_id", scope.ownerId)
                put("record_id", recordId)
                put("order_index", exercise.order)
                put("exercise_id", exercise.exerciseId.ifBlank { "manual" })
                put("exercise_name_snapshot", canonicalName(exercise.nameKo, identity))
                put("ui_part", exercise.uiPart.ifBlank { "other" })
                put("primary_sub_part_snapshot", exercise.primarySubPart)
                if (exercise.equipment.isBlank()) putNull("equipment_snapshot") else put("equipment_snapshot", exercise.equipment)
                put("record_type", FitnessRecordContract.normalizeRecordType(exercise.recordType))
                put("family_id", identity?.familyId)
                put("preset_id", identity?.presetId)
                put("canonical_variant_key", identity?.canonicalVariantKey)
                put("visual_variant_key", identity?.visualVariantKey)
                putNull("memo")
                put("created_at", createdAt)
                put("updated_at", createdAt)
                putNull("deleted_at")
                put("device_id", "android-local")
                put("contract_version", FitnessRecordContract.VERSION)
            }) != -1L) { "운동 종목을 저장하지 못했습니다." }
        }
    }

    private fun sessionInfoMetadata(scope: AccountScope, recordId: String): String =
        database.query("SELECT metadata FROM workout_records WHERE id = ? AND user_id = ? LIMIT 1",
            arrayOf(recordId, scope.ownerId)).use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0).orEmpty() else "{}"
        }

    fun deleteSession(scope: AccountScope, recordId: String): Boolean {
        val timestamp = now()
        val values = ContentValues().apply { put("deleted_at", timestamp); put("updated_at", timestamp) }
        database.beginTransaction()
        return try {
            val exercises = mutableListOf<String>()
            database.query("SELECT id FROM workout_exercises WHERE record_id = ? AND user_id = ? AND deleted_at IS NULL",
                arrayOf(recordId, scope.ownerId)).use { cursor ->
                while (cursor.moveToNext()) exercises += cursor.getString(0)
            }
            exercises.forEach { id -> database.update("workout_sets", 0, values,
                "workout_exercise_id = ? AND user_id = ? AND deleted_at IS NULL", arrayOf(id, scope.ownerId)) }
            database.update("workout_exercises", 0, values,
                "record_id = ? AND user_id = ? AND deleted_at IS NULL", arrayOf(recordId, scope.ownerId))
            val updated = database.update("workout_records", 0, values,
                "id = ? AND user_id = ? AND deleted_at IS NULL", arrayOf(recordId, scope.ownerId))
            database.setTransactionSuccessful()
            updated > 0
        } finally { database.endTransaction() }
    }

    fun sessionInfo(scope: AccountScope, recordId: String): SessionInfo? {
        return database.query(
            "SELECT exercise_name, date, duration_seconds, metadata, workout_type " +
                "FROM workout_records WHERE id = ? AND user_id = ? AND deleted_at IS NULL LIMIT 1",
            arrayOf(recordId, scope.ownerId)
        ).use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val metadata = cursor.getString(3)
            SessionInfo(
                cursor.getString(0).orEmpty(),
                cursor.getString(1).orEmpty(),
                metadataValue(metadata, "started_at"),
                metadataValue(metadata, "status"),
                resolvedDurationSeconds(
                    cursor.getString(1),
                    if (cursor.isNull(2)) null else cursor.getInt(2),
                    metadata
                ),
                cursor.getString(4).orEmpty()
            )
        }
    }

    fun exercises(scope: AccountScope, recordId: String): List<ExerciseRow> {
        val result = mutableListOf<ExerciseRow>()
        database.query(
            "SELECT id, exercise_id, order_index, exercise_name_snapshot, ui_part, " +
                "equipment_snapshot, record_type, family_id, preset_id, canonical_variant_key, " +
                "visual_variant_key FROM workout_exercises WHERE record_id = ? AND user_id = ? " +
                "AND deleted_at IS NULL ORDER BY order_index",
            arrayOf(recordId, scope.ownerId)
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val exerciseId = cursor.getString(1)
                val identity = identityForRow(
                    exerciseId,
                    cursor.getString(3),
                    cursor.getString(7),
                    cursor.getString(8),
                    cursor.getString(9),
                    cursor.getString(10)
                )
                result += ExerciseRow(
                    cursor.getString(0),
                    exerciseId,
                    cursor.getInt(2),
                    canonicalName(cursor.getString(3), identity),
                    cursor.getString(4).orEmpty(),
                    cursor.getString(5).orEmpty(),
                    FitnessRecordContract.normalizeRecordType(cursor.getString(6)),
                    identity
                )
            }
        }
        return result
    }

    fun sets(scope: AccountScope, exerciseId: String): List<SetRow> {
        val exercise = exerciseById(scope, exerciseId)
        val result = mutableListOf<SetRow>()
        database.query(
            "SELECT id, set_index, weight_kg, actual_reps, rir, rest_seconds, is_completed, " +
                "duration_seconds, distance_meters, assisted_weight_kg, added_weight_kg, volume_kg, " +
                "load_state, input_load_value, input_load_unit FROM workout_sets " +
                "WHERE workout_exercise_id = ? AND user_id = ? AND deleted_at IS NULL " +
                "ORDER BY set_index",
            arrayOf(exerciseId, scope.ownerId)
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val rawInput = if (cursor.isNull(13)) null else cursor.getDouble(13)
                val rawUnit = if (cursor.isNull(14)) null else MassUnit.parse(cursor.getString(14))
                val validInput = rawInput?.takeIf { it.isFinite() && it >= 0.0 }?.let { value ->
                    if (rawUnit == null) null else value to rawUnit
                }
                result += SetRow(
                    cursor.getString(0), cursor.getInt(1), number(cursor, 2), integer(cursor, 3),
                    nullableInt(cursor, 4), nullableInt(cursor, 5), cursor.getInt(6) == 1,
                    integer(cursor, 7), number(cursor, 8), number(cursor, 9), number(cursor, 10), number(cursor, 11),
                    loadStateForRead(exercise?.recordType, exercise?.familyIdentity,
                        if (cursor.isNull(12)) null else cursor.getString(12), number(cursor, 10)),
                    validInput?.first, validInput?.second
                )
            }
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
        database.query(
            "SELECT id, date, duration_seconds, metadata FROM workout_records WHERE user_id = ? " +
                "AND deleted_at IS NULL AND scope IN ('fitness', 'both') AND date = ?",
            arrayOf(scope.ownerId, date)
        ).use { cursor ->
            while (cursor.moveToNext()) {
                sessions++
                val metrics = metrics(scope, cursor.getString(0))
                sets += metrics.setCount
                volume += metrics.totalVolumeKg
                duration += resolvedDurationSeconds(
                    cursor.getString(1),
                    if (cursor.isNull(2)) null else cursor.getInt(2),
                    cursor.getString(3)
                )
            }
        }
        return DayMetrics(sessions, sets, volume, duration)
    }

    fun sessionsForDate(scope: AccountScope, date: String): List<String> {
        val result = mutableListOf<String>()
        database.query(
            "SELECT id FROM workout_records WHERE user_id = ? AND deleted_at IS NULL " +
                "AND scope IN ('fitness', 'both') AND date = ? ORDER BY updated_at DESC",
            arrayOf(scope.ownerId, date)
        ).use { cursor -> while (cursor.moveToNext()) result += cursor.getString(0) }
        return result
    }

    fun latestInProgress(scope: AccountScope): String? {
        return database.query(
            "SELECT id, metadata FROM workout_records WHERE user_id = ? AND deleted_at IS NULL " +
                "AND scope IN ('fitness', 'both') ORDER BY updated_at DESC LIMIT 20",
            arrayOf(scope.ownerId)
        ).use { cursor ->
            while (cursor.moveToNext()) {
                if (metadataValue(cursor.getString(1), "status") == "in_progress") return@use cursor.getString(0)
            }
            null
        }
    }

    fun latestCompletedForRoutine(scope: AccountScope, routineId: String, routineName: String): String? {
        return database.query(
            "SELECT date, metadata FROM workout_records WHERE user_id = ? AND deleted_at IS NULL " +
                "AND scope IN ('fitness', 'both') AND workout_type = 'strength' " +
                "ORDER BY date DESC, updated_at DESC",
            arrayOf(scope.ownerId)
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val metadata = cursor.getString(1)
                if (metadataValue(metadata, "status") != "completed") continue
                if (metadataValue(metadata, "routine_id") == routineId ||
                    metadataValue(metadata, "routine_name") == routineName) return@use cursor.getString(0)
            }
            null
        }
    }

    fun recentSessionVolumes(scope: AccountScope, currentRecordId: String, limit: Int): List<VolumePoint> {
        if (limit <= 0) return emptyList()
        val points = mutableListOf<VolumePoint>()
        database.query(
            "SELECT id, date, exercise_name, metadata, source_app FROM workout_records WHERE user_id = ? " +
                "AND deleted_at IS NULL AND scope IN ('fitness', 'both') AND id != ? " +
                "ORDER BY date DESC, updated_at DESC LIMIT $limit",
            arrayOf(scope.ownerId, currentRecordId)
        ).use { cursor ->
            while (cursor.moveToNext()) {
                if (metadataValue(cursor.getString(3), "status") != "completed" &&
                    cursor.getString(4) != "os") continue
                points += VolumePoint(
                    cursor.getString(1).orEmpty(),
                    cursor.getString(2).orEmpty(),
                    metrics(scope, cursor.getString(0)).totalVolumeKg
                )
            }
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
        database.query(
            "SELECT we.record_id, wr.date, wr.exercise_name FROM workout_exercises we " +
                "INNER JOIN workout_records wr ON wr.id = we.record_id AND wr.deleted_at IS NULL " +
                "WHERE wr.user_id = ? AND we.user_id = ? AND we.deleted_at IS NULL " +
                "AND we.record_id != ? AND wr.scope IN ('fitness', 'both') " +
                "AND ((we.exercise_id != 'manual' AND we.exercise_id = ?) OR " +
                "(we.exercise_id = 'manual' AND we.exercise_name_snapshot = ?)) " +
                "ORDER BY wr.date DESC, wr.updated_at DESC LIMIT 100",
            arrayOf(scope.ownerId, scope.ownerId, currentRecordId, exercise.exerciseId, exercise.name)
        ).use { cursor ->
            while (cursor.moveToNext() && points.size < limit) {
                val recordId = cursor.getString(0)
                val matchingExercise = exercises(scope, recordId).firstOrNull {
                    it.exerciseId == exercise.exerciseId ||
                        (it.exerciseId == "manual" && it.name == exercise.name)
                } ?: continue
                val volume = sets(scope, matchingExercise.id)
                    .filter { it.isCompleted }
                    .sumOf { volumeForSet(matchingExercise, it) }
                points[recordId] = VolumePoint(cursor.getString(1).orEmpty(), cursor.getString(2).orEmpty(), volume)
            }
        }
        return points.values.toList().asReversed()
    }

    fun lastExerciseHistory(scope: AccountScope, exercise: ExerciseRow, currentRecordId: String): History? {
        val row = database.query(
            "SELECT we.id, wr.date FROM workout_exercises we INNER JOIN workout_records wr " +
                "ON wr.id = we.record_id AND wr.deleted_at IS NULL WHERE wr.user_id = ? " +
                "AND we.user_id = ? AND we.deleted_at IS NULL AND we.record_id != ? " +
                "AND ((we.exercise_id != 'manual' AND we.exercise_id = ?) OR " +
                "(we.exercise_id = 'manual' AND we.exercise_name_snapshot = ?)) " +
                "ORDER BY wr.date DESC, wr.updated_at DESC LIMIT 1",
            arrayOf(scope.ownerId, scope.ownerId, currentRecordId, exercise.exerciseId, exercise.name)
        ).use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) to cursor.getString(1) else null }
            ?: return null
        val sets = sets(scope, row.first).filter { it.isCompleted }
        if (sets.isEmpty()) return null
        return History(row.second.orEmpty(), sets.sumOf { volumeForSet(exercise, it) }, sets)
    }

    fun bests(scope: AccountScope, exercise: ExerciseRow, currentRecordId: String): Bests {
        var maxWeight = 0.0
        var repsAtMax = 0
        var maxDate = ""
        var bestVolume = 0.0
        var bestVolumeDate = ""
        val sessionVolumes = linkedMapOf<String, Double>()
        val sessionDates = linkedMapOf<String, String>()
        database.query(
            "SELECT we.record_id, wr.date, ws.weight_kg, ws.actual_reps, ws.added_weight_kg, " +
                "ws.assisted_weight_kg, ws.load_state FROM workout_sets ws " +
                "INNER JOIN workout_exercises we ON we.id = ws.workout_exercise_id " +
                "INNER JOIN workout_records wr ON wr.id = we.record_id " +
                "WHERE wr.user_id = ? AND we.user_id = ? AND ws.user_id = ? " +
                "AND we.record_id != ? AND we.deleted_at IS NULL AND ws.deleted_at IS NULL " +
                "AND ws.is_completed = 1 AND ((we.exercise_id != 'manual' AND we.exercise_id = ?) OR " +
                "(we.exercise_id = 'manual' AND we.exercise_name_snapshot = ?))",
            arrayOf(scope.ownerId, scope.ownerId, scope.ownerId, currentRecordId,
                exercise.exerciseId, exercise.name)
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val recordId = cursor.getString(0)
                val date = cursor.getString(1).orEmpty()
                val weight = number(cursor, 2)
                val reps = integer(cursor, 3)
                val set = SetRow("", 0, weight, reps, null, null, true, 0, 0.0,
                    number(cursor, 5), number(cursor, 4), 0.0,
                    loadStateForRead(exercise.recordType, exercise.familyIdentity,
                        if (cursor.isNull(6)) null else cursor.getString(6), number(cursor, 4)),
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
        val values = ContentValues().apply {
            put("id", UUID.randomUUID().toString())
            put("user_id", scope.ownerId)
            put("workout_exercise_id", exerciseId)
            put("set_index", maxOf(1, setIndex))
            putNullable("target_reps", input.reps)
            putNullable("actual_reps", input.reps)
            putLoadStateValues(this, input, state)
            putNullable("duration_seconds", input.durationSeconds)
            putNullable("distance_meters", input.distanceMeters)
            putNullable("rest_seconds", input.restSeconds)
            putNullable("rir", input.rir)
            put("load_state", state?.id())
            putNullable("input_load_value", input.inputLoadValue)
            put("input_load_unit", input.inputLoadUnit?.id())
            put("is_completed", if (input.completed) 1 else 0)
            put("volume_kg", volumeForInput(exercise, input, state))
            put("created_at", now)
            put("updated_at", now)
            putNull("deleted_at")
            put("device_id", "android-local")
            put("contract_version", 1)
        }
        database.insert("workout_sets", 0, values)
        refreshRecordTotal(scope, recordId)
        return true
    }

    fun updateSet(scope: AccountScope, recordId: String, setId: String, input: WorkoutSetInput): Boolean {
        val exercise = exerciseForSet(scope, setId) ?: return false
        val state = input.loadState ?: defaultLoadState(exercise.recordType, input)
        val values = ContentValues().apply {
            putNullable("target_reps", input.reps)
            putNullable("actual_reps", input.reps)
            putLoadStateValues(this, input, state)
            putNullable("duration_seconds", input.durationSeconds)
            putNullable("distance_meters", input.distanceMeters)
            putNullable("rest_seconds", input.restSeconds)
            putNullable("rir", input.rir)
            put("load_state", state?.id())
            putNullable("input_load_value", input.inputLoadValue)
            put("input_load_unit", input.inputLoadUnit?.id())
            put("is_completed", if (input.completed) 1 else 0)
            put("volume_kg", volumeForInput(exercise, input, state))
            put("updated_at", now())
        }
        val updated = database.update("workout_sets", 0, values,
            "id = ? AND user_id = ? AND workout_exercise_id IN (SELECT id FROM workout_exercises WHERE record_id = ? AND user_id = ?) AND deleted_at IS NULL",
            arrayOf(setId, scope.ownerId, recordId, scope.ownerId))
        if (updated > 0) refreshRecordTotal(scope, recordId)
        return updated > 0
    }

    fun deleteSet(scope: AccountScope, recordId: String, setId: String): Boolean {
        val values = ContentValues().apply { put("deleted_at", now()); put("updated_at", now()) }
        val updated = database.update("workout_sets", 0, values,
            "id = ? AND user_id = ? AND workout_exercise_id IN (SELECT id FROM workout_exercises WHERE record_id = ? AND user_id = ?) AND deleted_at IS NULL",
            arrayOf(setId, scope.ownerId, recordId, scope.ownerId))
        if (updated > 0) refreshRecordTotal(scope, recordId)
        return updated > 0
    }

    fun deleteExercise(scope: AccountScope, recordId: String, exerciseId: String): Boolean {
        val values = ContentValues().apply { put("deleted_at", now()); put("updated_at", now()) }
        database.update("workout_sets", 0, values,
            "workout_exercise_id = ? AND user_id = ? AND deleted_at IS NULL", arrayOf(exerciseId, scope.ownerId))
        val updated = database.update("workout_exercises", 0, values,
            "id = ? AND record_id = ? AND user_id = ? AND deleted_at IS NULL",
            arrayOf(exerciseId, recordId, scope.ownerId))
        if (updated > 0) refreshRecordTotal(scope, recordId)
        return updated > 0
    }

    fun addExercise(scope: AccountScope, recordId: String,
                    exercise: WorkoutExerciseReplacement): Boolean {
        check(database.query(
            "SELECT 1 FROM workout_records WHERE id = ? AND user_id = ? AND deleted_at IS NULL LIMIT 1",
            arrayOf(recordId, scope.ownerId)
        ).use { it.moveToFirst() }) { "Workout record is not owned by the requested account." }
        val identity = exercise.familyIdentity
            ?: familyCatalog.identityForStorageExerciseId(exercise.masterExerciseId)
        val timestamp = now()
        val order = database.query(
            "SELECT COALESCE(MAX(order_index), 0) + 1 FROM workout_exercises " +
                "WHERE record_id = ? AND user_id = ? AND deleted_at IS NULL",
            arrayOf(recordId, scope.ownerId)
        ).use { if (it.moveToFirst()) it.getInt(0) else 1 }
        val values = ContentValues().apply {
            put("id", UUID.randomUUID().toString())
            put("user_id", scope.ownerId)
            put("record_id", recordId)
            put("order_index", order)
            put("exercise_id", exercise.masterExerciseId.ifBlank { "manual" })
            put("exercise_name_snapshot", canonicalName(exercise.nameKo, identity))
            put("ui_part", exercise.bodyPart?.labelKo() ?: identity?.defaultUiPart.orEmpty())
            put("primary_sub_part_snapshot", exercise.primarySubPart.orEmpty())
            put("equipment_snapshot", exercise.equipmentType?.labelKo())
            put("record_type", FitnessRecordContract.normalizeRecordType(exercise.recordType))
            put("family_id", identity?.familyId)
            put("preset_id", identity?.presetId)
            put("canonical_variant_key", identity?.canonicalVariantKey)
            put("visual_variant_key", identity?.visualVariantKey)
            putNull("memo")
            put("created_at", timestamp)
            put("updated_at", timestamp)
            putNull("deleted_at")
            put("device_id", "android-local")
        }
        return database.insert("workout_exercises", 0, values) != -1L
    }
    fun replaceExercise(scope: AccountScope, recordId: String, exerciseId: String, replacement: WorkoutExerciseReplacement): Boolean {
        val values = ContentValues().apply {
            put("exercise_id", replacement.masterExerciseId.ifBlank { "manual" })
            put("exercise_name_snapshot", replacement.nameKo)
            put("ui_part", replacement.bodyPart?.labelKo() ?: "other")
            put("primary_sub_part_snapshot", replacement.primarySubPart.orEmpty())
            put("equipment_snapshot", replacement.equipmentType?.labelKo())
            put("record_type", FitnessRecordContract.normalizeRecordType(replacement.recordType))
            put("family_id", replacement.familyIdentity?.familyId)
            put("preset_id", replacement.familyIdentity?.presetId)
            put("canonical_variant_key", replacement.familyIdentity?.canonicalVariantKey)
            put("visual_variant_key", replacement.familyIdentity?.visualVariantKey)
            put("updated_at", now())
        }
        val updated = database.update("workout_exercises", 0, values,
            "id = ? AND record_id = ? AND user_id = ? AND deleted_at IS NULL",
            arrayOf(exerciseId, recordId, scope.ownerId))
        if (updated > 0) refreshRecordTotal(scope, recordId)
        return updated > 0
    }

    fun complete(scope: AccountScope, recordId: String): Boolean {
        val info = sessionInfo(scope, recordId) ?: return false
        val metrics = metrics(scope, recordId)
        if (metrics.setCount == 0) return false
        val endedAt = now()
        val recordState = database.query(
            "SELECT metadata, source_app, scope, duration_seconds, is_backfilled FROM workout_records " +
                "WHERE id = ? AND user_id = ? AND deleted_at IS NULL",
            arrayOf(recordId, scope.ownerId)
        ).use { cursor ->
            if (cursor.moveToFirst()) listOf(
                cursor.getString(0), cursor.getString(1), cursor.getString(2),
                if (cursor.isNull(3)) "0" else cursor.getInt(3).toString(), cursor.getInt(4).toString()
            ) else listOf("{}", "fitness", "fitness", "0", "0")
        }
        val metadata = recordState[0]
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
        val values = ContentValues().apply {
            put("metadata", merged)
            val storedDuration = recordState[3].toIntOrNull() ?: 0
            val duration = if (recordState[4] == "1" && storedDuration > 0) storedDuration
                else maxOf(info.durationSeconds, elapsedSeconds(info.startedAt))
            put("duration_seconds", duration)
            put("total_volume_kg", metrics.totalVolumeKg)
            put("updated_at", endedAt)
            if (recordState[1] == "fitness") put("category", category)
            if (recordState[1] == "fitness") put("scope", "both") else put("scope", recordState[2])
        }
        database.update("workout_records", 0, values, "id = ? AND user_id = ? AND deleted_at IS NULL", arrayOf(recordId, scope.ownerId))
        return true
    }

    fun discard(scope: AccountScope, recordId: String): Boolean {
        val values = ContentValues().apply { put("deleted_at", now()); put("updated_at", now()) }
        return database.update("workout_records", 0, values,
            "id = ? AND user_id = ? AND deleted_at IS NULL", arrayOf(recordId, scope.ownerId)) > 0
    }

    private fun exerciseById(scope: AccountScope, id: String): ExerciseRow? =
        database.query(
            "SELECT record_id FROM workout_exercises WHERE id = ? AND user_id = ? AND deleted_at IS NULL LIMIT 1",
            arrayOf(id, scope.ownerId)
        ).use { cursor ->
            if (!cursor.moveToFirst()) null else exercises(scope, recordId = cursor.getString(0)).firstOrNull { it.id == id }
        }

    private fun exerciseForSet(scope: AccountScope, setId: String): ExerciseRow? =
        database.query(
            "SELECT workout_exercise_id FROM workout_sets WHERE id = ? AND user_id = ? AND deleted_at IS NULL LIMIT 1",
            arrayOf(setId, scope.ownerId)
        ).use { cursor -> if (!cursor.moveToFirst()) null else exerciseById(scope, cursor.getString(0)) }

    private fun requireOwnedExercise(scope: AccountScope, recordId: String, exerciseId: String) {
        check(database.query(
            "SELECT 1 FROM workout_exercises WHERE id = ? AND record_id = ? AND user_id = ? AND deleted_at IS NULL LIMIT 1",
            arrayOf(exerciseId, recordId, scope.ownerId)
        ).use { it.moveToFirst() }) { "Workout exercise is not owned by the requested account." }
    }

    private fun refreshRecordTotal(scope: AccountScope, recordId: String) {
        val values = ContentValues().apply {
            put("total_volume_kg", metrics(scope, recordId).totalVolumeKg)
            put("updated_at", now())
        }
        database.update("workout_records", 0, values, "id = ? AND user_id = ? AND deleted_at IS NULL", arrayOf(recordId, scope.ownerId))
    }

    private fun categoryFor(scope: AccountScope, recordId: String): String {
        val values = mutableListOf<String>()
        database.query("SELECT ui_part FROM workout_exercises WHERE record_id = ? AND user_id = ? AND deleted_at IS NULL ORDER BY order_index", arrayOf(recordId, scope.ownerId)).use { cursor ->
            while (cursor.moveToNext()) {
                val value = when (cursor.getString(0)?.lowercase()) {
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

    private fun putLoadStateValues(values: ContentValues, input: WorkoutSetInput, state: LoadState?) {
        when (state) {
            LoadState.EXTERNAL_LOAD -> {
                values.putNullable("weight_kg", input.weightKg)
                values.putNull("assisted_weight_kg")
                values.putNull("added_weight_kg")
            }
            LoadState.ADDED_WEIGHT -> {
                values.putNull("weight_kg")
                values.putNull("assisted_weight_kg")
                values.putNullable("added_weight_kg", input.addedWeightKg)
            }
            LoadState.ASSISTED, LoadState.BAND_ASSISTED -> {
                values.putNull("weight_kg")
                values.putNullable("assisted_weight_kg", input.assistedWeightKg)
                values.putNull("added_weight_kg")
            }
            LoadState.BAND_RESISTED -> {
                values.putNullable("weight_kg", input.weightKg)
                values.putNull("assisted_weight_kg")
                values.putNull("added_weight_kg")
            }
            else -> {
                values.putNullable("weight_kg", input.weightKg)
                values.putNullable("assisted_weight_kg", input.assistedWeightKg)
                values.putNullable("added_weight_kg", input.addedWeightKg)
            }
        }
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
    private fun number(cursor: android.database.Cursor, index: Int): Double = if (cursor.isNull(index)) 0.0 else cursor.getDouble(index)
    private fun integer(cursor: android.database.Cursor, index: Int): Int = if (cursor.isNull(index)) 0 else cursor.getInt(index)
    private fun nullableInt(cursor: android.database.Cursor, index: Int): Int? = if (cursor.isNull(index)) null else cursor.getInt(index)
    private fun ContentValues.putNullable(key: String, value: Number?) {
        when (value) {
            null -> putNull(key)
            is Int -> put(key, value)
            is Long -> put(key, value)
            is Float -> put(key, value)
            is Double -> put(key, value)
            is Short -> put(key, value)
            is Byte -> put(key, value)
            else -> put(key, value.toDouble())
        }
    }
    private fun ContentValues.putNull(key: String) = putNull(key)
}

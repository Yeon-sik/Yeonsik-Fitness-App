package com.yeonsik.fitnessapp.feature.workout.data

import com.yeonsik.fitness.shared.core.account.AccountScope
import com.yeonsik.fitness.shared.feature.cardio.api.CardioRepositoryApi
import com.yeonsik.fitness.shared.feature.exercise.model.LoadState
import com.yeonsik.fitness.shared.feature.dailyworkout.DailyHeartRateSample
import com.yeonsik.fitness.shared.feature.dailyworkout.DailyWorkoutLocalReader
import com.yeonsik.fitness.shared.feature.dailyworkout.LocalDailyWorkouts
import com.yeonsik.fitness.shared.feature.dailyworkout.LocalWorkoutReference
import com.yeonsik.fitness.shared.feature.dailyworkout.DailyWorkoutExercise
import com.yeonsik.fitness.shared.feature.dailyworkout.DailyWorkoutKind
import com.yeonsik.fitness.shared.feature.dailyworkout.DailyWorkoutSet
import com.yeonsik.fitness.shared.feature.dailyworkout.DailyWorkoutSource
import com.yeonsik.fitness.shared.feature.dailyworkout.WorkoutDay
import com.yeonsik.fitness.shared.feature.dailyworkout.WorkoutOrigin
import com.yeonsik.fitness.shared.feature.workout.api.WorkoutRepositoryApi
import com.yeonsik.fitnessapp.core.database.FitnessRoomDatabase
import com.yeonsik.fitnessapp.feature.dailyworkout.toDailyWorkoutKind
import com.yeonsik.fitnessapp.integration.wearable.WearableWorkoutRepository
import org.json.JSONObject
import java.time.Instant
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.concurrent.Callable

internal class WorkoutDailyReadRepository(
    private val database: FitnessRoomDatabase,
    private val workouts: WorkoutRepositoryApi,
    private val cardio: CardioRepositoryApi,
    private val scope: AccountScope
) : DailyWorkoutLocalReader {
    private val wearables = WearableWorkoutRepository(database)

    override fun findRecord(recordId: String): LocalWorkoutReference? {
        val row = database.workoutRoomDao().visibleRecord(recordId, scope.ownerId) ?: return null
        return reference(row.date, row.id, metadata(row.metadata))
    }

    private fun metadata(json: String?) = runCatching { JSONObject(json.orEmpty()) }.getOrElse { JSONObject() }

    private fun reference(date: String, id: String, metadata: JSONObject): LocalWorkoutReference {
        val fleek = metadata.optString("fleek_source_key").takeIf { it.isNotBlank() }
        return if (fleek == null) LocalWorkoutReference(date, WorkoutOrigin.FITNESS, id)
            else LocalWorkoutReference(date, WorkoutOrigin.FLEEK, fleek)
    }

    override fun read(day: WorkoutDay): LocalDailyWorkouts = database.runInTransaction(Callable {
        val native = completedWorkouts(day)
        val stored = wearables.startingBetween(scope.ownerId, Instant.ofEpochMilli(day.start.toEpochMilliseconds()),
            Instant.ofEpochMilli(day.end.toEpochMilliseconds()))
        val linked = mutableMapOf<String, String>()
        val samples = mutableMapOf<String, List<DailyHeartRateSample>>()
        val watch = stored.map { row ->
            val source = DailyWorkoutSource(
                origin = WorkoutOrigin.HEALTH_CONNECT, writer = row.origin, id = row.providerId, title = row.title,
                kind = row.exerciseType.toDailyWorkoutKind(), startMillis = row.start.toEpochMilli(),
                endMillis = row.end.toEpochMilli(), durationSeconds = Duration.between(row.start, row.end).seconds.toInt()
            )
            row.linkedRecordId?.let { native[it] }?.let { linked[source.key] = it.key }
            samples[source.key] = row.samples.map { DailyHeartRateSample(row.origin, it.time.toEpochMilli(), it.bpm.toDouble()) }
            source
        }
        LocalDailyWorkouts(native.values + watch, samples, linked)
    })

    private fun completedWorkouts(day: WorkoutDay): Map<String, DailyWorkoutSource> {
        val date = LocalDate.parse(day.date.toString())
        val zone = ZoneId.of(day.zone.id)
        return database.workoutRoomDao().visibleCompletedRecordsBetween(scope.ownerId, date.toString(), date.toString()).mapNotNull { row ->
            val workout = workouts.loadSession(scope, row.id) ?: return@mapNotNull null
            val metadata = metadata(row.metadata)
            val ref = reference(row.date, row.id, metadata)
            val isFleek = ref.origin == WorkoutOrigin.FLEEK
            val cardioSession = if (row.workoutType == "cardio") cardio.loadSession(scope, row.id) else null
            var start = cardioSession?.startedAtEpochMillis ?: parseTime(workout.startedAt, date, zone)
            if (isFleek) {
                val localMidnight = date.atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant().toEpochMilli()
                if (!metadata.optBoolean("fleek_has_reported_start_time", start != localMidnight)) start = null
            }
            val end = if (isFleek) null else parseTime(workout.completedAt.orEmpty(), date, zone)
            row.id to DailyWorkoutSource(
                origin = ref.origin,
                writer = ref.origin.label,
                id = ref.id,
                title = workout.title.ifBlank { "운동 기록" },
                kind = when (row.workoutType) {
                    "strength" -> DailyWorkoutKind.STRENGTH
                    "cardio" -> when (cardioSession?.activityId ?: row.category) {
                        "running" -> DailyWorkoutKind.RUNNING
                        "walking" -> DailyWorkoutKind.WALKING
                        "cycling" -> DailyWorkoutKind.CYCLING
                        else -> DailyWorkoutKind.CARDIO
                    }
                    else -> DailyWorkoutKind.OTHER
                },
                startMillis = start,
                endMillis = end?.takeIf { start != null && it > start },
                durationSeconds = workout.durationSeconds.takeIf { it > 0 && !isFleek },
                distanceMeters = cardioSession?.recordedDistanceMeters?.takeIf { it.isFinite() && it > 0 },
                manualHeartRateBpm = row.averageHeartRate?.takeIf { it.isFinite() && it > 0 },
                exercises = workout.exercises.filter { it.completedSets.isNotEmpty() }.map { entry ->
                    DailyWorkoutExercise(entry.name, entry.completedSets.map { set ->
                        DailyWorkoutSet(
                            weightKg = when (set.loadState) {
                                LoadState.ADDED_WEIGHT -> set.addedWeightKg
                                LoadState.ASSISTED -> set.assistedWeightKg
                                else -> set.weightKg
                            }.takeIf { it.isFinite() && it > 0 },
                            reps = set.actualReps.takeIf { it > 0 },
                            rir = set.rir,
                            durationSeconds = set.durationSeconds.takeIf { it > 0 },
                            distanceMeters = set.distanceMeters.takeIf { it.isFinite() && it > 0 },
                            loadState = set.loadState?.id(),
                            setType = set.setType.id
                        )
                    })
                }
            )
        }.toMap()
    }

    private fun parseTime(value: String, date: LocalDate, zone: ZoneId): Long? =
        runCatching { Instant.parse(value).toEpochMilli() }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(value).toInstant().toEpochMilli() }.getOrNull()
            ?: runCatching { LocalDateTime.parse(value).atZone(zone).toInstant().toEpochMilli() }.getOrNull()
            ?: runCatching { date.atTime(LocalTime.parse(value)).atZone(zone).toInstant().toEpochMilli() }.getOrNull()
}

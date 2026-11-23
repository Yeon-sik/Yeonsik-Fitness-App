package com.yeonsik.fitnessapp.feature.workout.data

import com.yeonsik.fitness.shared.core.account.AccountScope
import com.yeonsik.fitness.shared.feature.cardio.api.CardioRepositoryApi
import com.yeonsik.fitness.shared.feature.exercise.model.LoadState
import com.yeonsik.fitness.shared.feature.dailyworkout.DailyWorkoutExercise
import com.yeonsik.fitness.shared.feature.dailyworkout.DailyWorkoutKind
import com.yeonsik.fitness.shared.feature.dailyworkout.DailyWorkoutSet
import com.yeonsik.fitness.shared.feature.dailyworkout.DailyWorkoutSource
import com.yeonsik.fitness.shared.feature.dailyworkout.WorkoutDay
import com.yeonsik.fitness.shared.feature.dailyworkout.WorkoutOrigin
import com.yeonsik.fitness.shared.feature.workout.api.WorkoutRepositoryApi
import com.yeonsik.fitnessapp.core.database.FitnessRoomDatabase
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.concurrent.Callable

internal class WorkoutDailyReadRepository(
    private val database: FitnessRoomDatabase,
    private val workouts: WorkoutRepositoryApi,
    private val cardio: CardioRepositoryApi
) {
    fun completedWorkouts(scope: AccountScope, day: WorkoutDay): List<DailyWorkoutSource> = database.runInTransaction(Callable {
        val date = LocalDate.parse(day.date.toString())
        val zone = ZoneId.of(day.zone.id)
        database.workoutRoomDao().visibleCompletedRecordsBetween(scope.ownerId, date.toString(), date.toString()).mapNotNull { row ->
            val workout = workouts.loadSession(scope, row.id) ?: return@mapNotNull null
            val metadata = runCatching { JSONObject(row.metadata) }.getOrElse { JSONObject() }
            val fleekKey = metadata.optString("fleek_source_key").takeIf { it.isNotBlank() }
            val origin = if (fleekKey != null) WorkoutOrigin.FLEEK else WorkoutOrigin.FITNESS
            val cardioSession = if (row.workoutType == "cardio") cardio.loadSession(scope, row.id) else null
            var start = cardioSession?.startedAtEpochMillis ?: parseTime(workout.startedAt, date, zone)
            if (fleekKey != null) {
                val localMidnight = date.atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant().toEpochMilli()
                if (!metadata.optBoolean("fleek_has_reported_start_time", start != localMidnight)) start = null
            }
            val end = if (fleekKey == null) parseTime(workout.completedAt.orEmpty(), date, zone) else null
            DailyWorkoutSource(
                origin = origin,
                writer = origin.label,
                id = fleekKey ?: row.id,
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
                durationSeconds = workout.durationSeconds.takeIf { it > 0 && fleekKey == null },
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
        }
    })

    private fun parseTime(value: String, date: LocalDate, zone: ZoneId): Long? =
        runCatching { Instant.parse(value).toEpochMilli() }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(value).toInstant().toEpochMilli() }.getOrNull()
            ?: runCatching { LocalDateTime.parse(value).atZone(zone).toInstant().toEpochMilli() }.getOrNull()
            ?: runCatching { date.atTime(LocalTime.parse(value)).atZone(zone).toInstant().toEpochMilli() }.getOrNull()
}

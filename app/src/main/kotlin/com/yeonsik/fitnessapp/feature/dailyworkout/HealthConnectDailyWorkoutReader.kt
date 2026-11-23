package com.yeonsik.fitnessapp.feature.dailyworkout

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.aggregate.AggregateMetric
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.yeonsik.fitness.shared.feature.dailyworkout.DailyHeartRateSample
import com.yeonsik.fitness.shared.feature.dailyworkout.DailyWearableRecords
import com.yeonsik.fitness.shared.feature.dailyworkout.DailyWorkoutKind
import com.yeonsik.fitness.shared.feature.dailyworkout.DailyWorkoutSource
import com.yeonsik.fitness.shared.feature.dailyworkout.WorkoutDay
import com.yeonsik.fitness.shared.feature.dailyworkout.WorkoutOrigin
import java.time.Duration
import java.time.Instant
import kotlin.reflect.KClass

internal class HealthConnectDailyWorkoutReader(private val context: Context) {
    private val readExercise = HealthPermission.getReadPermission(ExerciseSessionRecord::class)
    private val readHeart = HealthPermission.getReadPermission(HeartRateRecord::class)
    private val readDistance = HealthPermission.getReadPermission(DistanceRecord::class)
    private val readEnergy = HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class)

    suspend fun read(day: WorkoutDay, request: suspend (Set<String>) -> Set<String>): DailyWearableRecords {
        when (HealthConnectClient.getSdkStatus(context)) {
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED ->
                throw IllegalArgumentException("Health Connect를 설치하거나 업데이트해 주세요.")
            HealthConnectClient.SDK_AVAILABLE -> Unit
            else -> throw IllegalArgumentException("이 기기에서 Health Connect를 사용할 수 없습니다.")
        }
        val client = HealthConnectClient.getOrCreate(context)
        val available = setOf(readExercise, readHeart, readDistance, readEnergy).toMutableSet()
        if (client.features.getFeatureStatus(HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_HISTORY) ==
            HealthConnectFeatures.FEATURE_STATUS_AVAILABLE) available.add(HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY)
        val previous = client.permissionController.getGrantedPermissions()
        val needed = if (readExercise !in previous && readHeart !in previous) available else {
            val history = HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY
            if (history in available && history !in previous && day.start.toEpochMilliseconds() <
                Instant.now().minus(Duration.ofDays(30)).toEpochMilli()) setOf(history) else emptySet()
        }
        val allowed = if (needed.isEmpty()) previous else previous + request(needed)
        require(readExercise in allowed || readHeart in allowed) { "Health Connect에서 운동 또는 심박 읽기를 허용해 주세요." }
        val range = TimeRangeFilter.between(Instant.ofEpochMilli(day.start.toEpochMilliseconds()), Instant.ofEpochMilli(day.end.toEpochMilliseconds()))
        val records = if (readExercise in allowed) client.records(ExerciseSessionRecord::class, range) else emptyList()
        val workouts = records.map { session ->
            val kind = session.exerciseType.toDailyWorkoutKind()
            val metrics = buildSet<AggregateMetric<*>> {
                if (readDistance in allowed) add(DistanceRecord.DISTANCE_TOTAL)
                if (readEnergy in allowed) add(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)
            }
            val totals = metrics.takeIf { it.isNotEmpty() }?.let {
                client.aggregate(AggregateRequest(it, TimeRangeFilter.between(session.startTime, session.endTime),
                    dataOriginFilter = setOf(session.metadata.dataOrigin)))
            }
            DailyWorkoutSource(
                WorkoutOrigin.HEALTH_CONNECT, session.metadata.dataOrigin.packageName, session.metadata.id,
                session.title?.takeIf(String::isNotBlank) ?: kind.label,
                kind, session.startTime.toEpochMilli(), session.endTime.toEpochMilli(),
                Duration.between(session.startTime, session.endTime).seconds.toInt(),
                totals?.get(DistanceRecord.DISTANCE_TOTAL)?.inMeters,
                totals?.get(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)?.inKilocalories
            )
        }
        val sampleEnd = maxOf(day.end.toEpochMilliseconds(), workouts.mapNotNull { it.endMillis }.maxOrNull() ?: Long.MIN_VALUE)
        val sampleRange = TimeRangeFilter.between(Instant.ofEpochMilli(day.start.toEpochMilliseconds()), Instant.ofEpochMilli(sampleEnd))
        val heartRate = if (readHeart in allowed) client.records(HeartRateRecord::class, sampleRange).flatMap { record ->
            record.samples.map { DailyHeartRateSample(record.metadata.dataOrigin.packageName, it.time.toEpochMilli(), it.beatsPerMinute.toDouble()) }
        } else emptyList()
        return DailyWearableRecords(workouts, heartRate)
    }

    private suspend fun <T : Record> HealthConnectClient.records(type: KClass<T>, range: TimeRangeFilter): List<T> {
        val records = mutableListOf<T>()
        var pageToken: String? = null
        do {
            val page = readRecords(ReadRecordsRequest(type, range, pageToken = pageToken))
            records.addAll(page.records)
            pageToken = page.pageToken
        } while (pageToken != null)
        return records
    }
}

private fun Int.toDailyWorkoutKind(): DailyWorkoutKind = when (this) {
    ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING, ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING -> DailyWorkoutKind.STRENGTH
    ExerciseSessionRecord.EXERCISE_TYPE_RUNNING, ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL -> DailyWorkoutKind.RUNNING
    ExerciseSessionRecord.EXERCISE_TYPE_WALKING -> DailyWorkoutKind.WALKING
    ExerciseSessionRecord.EXERCISE_TYPE_BIKING, ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY -> DailyWorkoutKind.CYCLING
    ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL, ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER -> DailyWorkoutKind.SWIMMING
    ExerciseSessionRecord.EXERCISE_TYPE_ELLIPTICAL, ExerciseSessionRecord.EXERCISE_TYPE_ROWING_MACHINE,
    ExerciseSessionRecord.EXERCISE_TYPE_STAIR_CLIMBING_MACHINE -> DailyWorkoutKind.CARDIO
    else -> DailyWorkoutKind.OTHER
}

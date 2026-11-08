package com.yeonsik.fitnessapp.integration.wearable

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.*
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.*
import kotlin.reflect.KClass

class HealthConnectWorkoutReader(private val context: Context) {
    val permissions = setOf(HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class))
    private val client get() = HealthConnectClient.getOrCreate(context)
    fun available() = HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE
    suspend fun read(date: LocalDate, zone: ZoneId): List<WearableWorkout> {
        check(available())
        check(client.permissionController.getGrantedPermissions().containsAll(permissions))
        val sessions = all(ExerciseSessionRecord::class, date.atStartOfDay(zone).toInstant(),
            date.plusDays(1).atStartOfDay(zone).toInstant())
        return sessions.sortedByDescending { it.startTime }.map { session ->
            val origin = session.metadata.dataOrigin.packageName
            val records = all(HeartRateRecord::class, session.startTime, session.endTime, origin)
            WearableWorkout(origin, session.metadata.id,
                session.title ?: if (session.exerciseType == ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING) "근력 운동" else "외부 운동",
                session.startTime, session.endTime, session.exerciseType,
                measuredPulses(session.startTime, session.endTime, records.flatMap { record ->
                    record.samples.map { MeasuredPulse(it.time, it.beatsPerMinute) }
                }))
        }
    }
    private suspend fun <T: Record> all(type: KClass<T>, start: Instant, end: Instant, origin: String? = null): List<T> {
        val rows = mutableListOf<T>()
        val seen = mutableSetOf<String>()
        var token: String? = null
        do {
            token?.let { check(seen.add(it)) }
            check(seen.size < 100)
            val page = client.readRecords(ReadRecordsRequest(type, TimeRangeFilter.between(start, end),
                dataOriginFilter = origin?.let { setOf(DataOrigin(it)) } ?: emptySet(), pageToken = token))
            rows += page.records
            token = page.pageToken
        } while (token != null)
        return rows
    }
}

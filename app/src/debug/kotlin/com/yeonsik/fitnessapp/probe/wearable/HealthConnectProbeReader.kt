package com.yeonsik.fitnessapp.probe.wearable

import android.content.Context
import android.content.pm.PackageManager
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.changes.DeletionChange
import androidx.health.connect.client.changes.UpsertionChange
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ChangesTokenRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.yeonsik.fitnessapp.probe.wearable.analysis.ChangesObservation
import com.yeonsik.fitnessapp.probe.wearable.analysis.ObservedExerciseSession
import com.yeonsik.fitnessapp.probe.wearable.analysis.ObservedHeartRateRecord
import com.yeonsik.fitnessapp.probe.wearable.analysis.ObservedMetadata
import com.yeonsik.fitnessapp.probe.wearable.analysis.ProbeIdMasker
import com.yeonsik.fitnessapp.probe.wearable.analysis.RawStepsTotal
import com.yeonsik.fitnessapp.probe.wearable.analysis.ReadOutcome
import com.yeonsik.fitnessapp.probe.wearable.analysis.ReadResult
import com.yeonsik.fitnessapp.probe.wearable.analysis.StepsObservation
import com.yeonsik.fitnessapp.probe.wearable.analysis.TimeInterval
import java.time.Instant
import kotlin.coroutines.cancellation.CancellationException
import kotlin.reflect.KClass

/**
 * Read-only Health Connect access for the PR0 probe (debug builds only).
 *
 * It never writes to Health Connect, never opens the Fitness Room database and never sends data
 * off the device. Heart-rate values are dropped while mapping; only sample times are kept.
 * Exception messages are not kept either, only the exception class name.
 */
class HealthConnectProbeReader(private val context: Context) {

    val readPermissions: Set<String> = setOf(
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        HealthPermission.getReadPermission(StepsRecord::class),
    )

    private val client: HealthConnectClient by lazy { HealthConnectClient.getOrCreate(context) }

    /** `null` when Health Connect is available, otherwise the outcome every read would report. */
    fun availabilityOutcome(): ReadOutcome? = when (HealthConnectClient.getSdkStatus(context)) {
        HealthConnectClient.SDK_AVAILABLE -> null
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> ReadOutcome.HC_UPDATE_REQUIRED
        else -> ReadOutcome.HC_UNAVAILABLE
    }

    suspend fun grantedPermissions(): Set<String> {
        if (availabilityOutcome() != null) return emptySet()
        return try {
            client.permissionController.getGrantedPermissions()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptySet()
        }
    }

    suspend fun readHeartRate(interval: TimeInterval, masker: ProbeIdMasker): ReadResult<ObservedHeartRateRecord> =
        readAll(HeartRateRecord::class, interval) { record ->
            ObservedHeartRateRecord(
                metadata = record.metadata.observe(masker),
                startAt = record.startTime,
                endAt = record.endTime,
                sampleTimes = record.samples.map { it.time },
            )
        }

    suspend fun readExerciseSessions(interval: TimeInterval, masker: ProbeIdMasker): ReadResult<ObservedExerciseSession> =
        readAll(ExerciseSessionRecord::class, interval) { record ->
            ObservedExerciseSession(
                metadata = record.metadata.observe(masker),
                startAt = record.startTime,
                endAt = record.endTime,
                exerciseType = record.exerciseType,
                segmentCount = record.segments.size,
            )
        }

    suspend fun readSteps(interval: TimeInterval): ReadResult<StepsObservation> = guarded {
        val filter = TimeRangeFilter.between(interval.start, interval.end)
        val aggregate = client.aggregate(
            AggregateRequest(
                metrics = setOf(StepsRecord.COUNT_TOTAL),
                timeRangeFilter = filter,
                dataOriginFilter = emptySet(),
            )
        )
        val rawByOrigin = sortedMapOf<String, RawStepsTotal>()
        var pageToken: String? = null
        var pages = 0
        do {
            val response = client.readRecords(
                ReadRecordsRequest(StepsRecord::class, filter, pageToken = pageToken)
            )
            for (record in response.records) {
                val origin = record.metadata.dataOrigin.packageName
                val current = rawByOrigin[origin] ?: RawStepsTotal(records = 0, steps = 0)
                rawByOrigin[origin] = RawStepsTotal(current.records + 1, current.steps + record.count)
            }
            pageToken = response.pageToken
            pages++
        } while (pageToken != null && pages < MAX_PAGES)

        val total = aggregate[StepsRecord.COUNT_TOTAL]
        val observation = StepsObservation(
            aggregateTotal = total,
            aggregateOrigins = aggregate.dataOrigins.map { it.packageName }.sorted(),
            rawByOrigin = rawByOrigin,
        )
        val items = if ((total ?: 0L) == 0L && rawByOrigin.isEmpty()) emptyList() else listOf(observation)
        ReadResult.success(items, pages, complete = pageToken == null)
    }

    suspend fun issueHeartRateChangesToken(): ReadResult<String> = guarded {
        val token = client.getChangesToken(ChangesTokenRequest(recordTypes = setOf(HeartRateRecord::class)))
        ReadResult(ReadOutcome.SUCCESS, items = listOf(token), pages = 1, complete = true)
    }

    /** Reads heart-rate changes since [token]. The returned next token is in [HeartRateChanges.nextToken]. */
    suspend fun readHeartRateChanges(
        token: String,
        issuedAt: Instant?,
        masker: ProbeIdMasker,
        lastQueryMaskedIds: Set<String>,
    ): HeartRateChanges {
        val queriedAt = Instant.now()
        availabilityOutcome()?.let { outcome ->
            return HeartRateChanges(failedObservation(outcome, issuedAt, queriedAt, null), nextToken = null)
        }
        return try {
            var current = token
            var upsertions = 0
            val deletions = mutableListOf<String>()
            var pages = 0
            var hasMore = false
            var expired = false
            do {
                val response = client.getChanges(current)
                if (response.changesTokenExpired) {
                    expired = true
                    break
                }
                for (change in response.changes) {
                    when (change) {
                        is UpsertionChange -> upsertions++
                        is DeletionChange -> deletions += masker.mask(change.recordId)
                        else -> Unit
                    }
                }
                current = response.nextChangesToken
                hasMore = response.hasMore
                pages++
            } while (hasMore && pages < MAX_CHANGE_PAGES)

            val outcome = if (upsertions == 0 && deletions.isEmpty()) ReadOutcome.EMPTY_SUCCESS else ReadOutcome.SUCCESS
            HeartRateChanges(
                observation = ChangesObservation(
                    outcome = outcome,
                    issuedAt = issuedAt,
                    queriedAt = queriedAt,
                    tokenExpired = expired,
                    upsertions = upsertions,
                    deletionMaskedIds = deletions,
                    deletionsMatchingLastQuery = deletions.count { it in lastQueryMaskedIds },
                    pages = pages,
                    hasMoreRemaining = hasMore,
                    failureType = null,
                ),
                nextToken = if (expired) null else current,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            HeartRateChanges(failedObservation(classify(e), issuedAt, queriedAt, e.javaClass.simpleName), nextToken = token)
        }
    }

    /** Installed versions of the packages PR0 records; `null` means not installed or not visible. */
    fun packageVersions(): Map<String, String?> = VERSION_PACKAGES.associateWith { packageName ->
        try {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(packageName, 0).versionName ?: "versionName 없음"
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }

    private suspend fun <T : Record, R> readAll(
        type: KClass<T>,
        interval: TimeInterval,
        map: (T) -> R,
    ): ReadResult<R> = guarded {
        val filter = TimeRangeFilter.between(interval.start, interval.end)
        val items = mutableListOf<R>()
        var pageToken: String? = null
        var pages = 0
        do {
            val response = client.readRecords(ReadRecordsRequest(type, filter, pageToken = pageToken))
            response.records.mapTo(items, map)
            pageToken = response.pageToken
            pages++
        } while (pageToken != null && pages < MAX_PAGES)
        ReadResult.success(items, pages, complete = pageToken == null)
    }

    private suspend fun <R> guarded(block: suspend () -> ReadResult<R>): ReadResult<R> {
        availabilityOutcome()?.let { return ReadResult(outcome = it) }
        return try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ReadResult(outcome = classify(e), failureType = e.javaClass.simpleName)
        }
    }

    private fun classify(error: Exception): ReadOutcome = when (error) {
        is SecurityException -> ReadOutcome.PERMISSION_DENIED
        is UnsupportedOperationException -> ReadOutcome.UNSUPPORTED
        else -> ReadOutcome.FAILURE
    }

    private fun failedObservation(
        outcome: ReadOutcome,
        issuedAt: Instant?,
        queriedAt: Instant,
        failureType: String?,
    ) = ChangesObservation(
        outcome = outcome,
        issuedAt = issuedAt,
        queriedAt = queriedAt,
        tokenExpired = false,
        upsertions = 0,
        deletionMaskedIds = emptyList(),
        deletionsMatchingLastQuery = 0,
        pages = 0,
        hasMoreRemaining = false,
        failureType = failureType,
    )

    private fun Metadata.observe(masker: ProbeIdMasker) = ObservedMetadata(
        maskedId = id.takeIf { it.isNotBlank() }?.let(masker::mask),
        originPackage = dataOrigin.packageName,
        recordingMethod = recordingMethod,
        deviceType = device?.type,
        deviceManufacturer = device?.manufacturer,
        deviceModel = device?.model,
        hasClientRecordId = !clientRecordId.isNullOrBlank(),
        clientRecordVersion = clientRecordVersion,
        lastModifiedAt = lastModifiedTime,
    )

    data class HeartRateChanges(val observation: ChangesObservation, val nextToken: String?)

    companion object {
        private const val MAX_PAGES = 50
        private const val MAX_CHANGE_PAGES = 20

        /**
         * Health Connect (APK and platform controller) and Samsung Health. Every name here is
         * also listed under `<queries>` in the debug manifest so the version can be read.
         */
        val VERSION_PACKAGES: List<String> = listOf(
            "com.google.android.apps.healthdata",
            "com.google.android.healthconnect.controller",
            "com.android.healthconnect.controller",
            "com.sec.android.app.shealth",
        )
    }
}

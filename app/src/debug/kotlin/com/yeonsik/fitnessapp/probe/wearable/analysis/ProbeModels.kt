package com.yeonsik.fitnessapp.probe.wearable.analysis

import java.time.Instant
import java.time.ZoneId

/**
 * How one Health Connect read ended. PR0 must keep these apart: an empty success is not a
 * permission failure, and neither is evidence that the source has no data.
 */
enum class ReadOutcome {
    SUCCESS,
    EMPTY_SUCCESS,
    PERMISSION_DENIED,
    HC_UNAVAILABLE,
    HC_UPDATE_REQUIRED,
    UNSUPPORTED,
    FAILURE,
}

/**
 * Record metadata as the probe keeps it. The Health Connect record id is never stored: only a
 * salted, device-local mask of it ([maskedId]) so repeated queries can be compared.
 */
data class ObservedMetadata(
    val maskedId: String?,
    val originPackage: String,
    val recordingMethod: Int,
    val deviceType: Int?,
    val deviceManufacturer: String?,
    val deviceModel: String?,
    val hasClientRecordId: Boolean,
    val clientRecordVersion: Long,
    val lastModifiedAt: Instant,
)

/** A heart-rate record reduced to sample times. Beats-per-minute values are dropped on read. */
data class ObservedHeartRateRecord(
    val metadata: ObservedMetadata,
    val startAt: Instant,
    val endAt: Instant,
    val sampleTimes: List<Instant>,
)

data class ObservedExerciseSession(
    val metadata: ObservedMetadata,
    val startAt: Instant,
    val endAt: Instant,
    val exerciseType: Int,
    val segmentCount: Int,
)

data class RawStepsTotal(val records: Int, val steps: Long)

/** Auxiliary steps observation; it never satisfies the heart-rate gate. */
data class StepsObservation(
    val aggregateTotal: Long?,
    val aggregateOrigins: List<String>,
    val rawByOrigin: Map<String, RawStepsTotal>,
)

data class ReadResult<T>(
    val outcome: ReadOutcome,
    val items: List<T> = emptyList(),
    val pages: Int = 0,
    val complete: Boolean = false,
    val failureType: String? = null,
) {
    companion object {
        fun <T> success(items: List<T>, pages: Int, complete: Boolean): ReadResult<T> = ReadResult(
            outcome = if (items.isEmpty()) ReadOutcome.EMPTY_SUCCESS else ReadOutcome.SUCCESS,
            items = items,
            pages = pages,
            complete = complete,
        )
    }
}

/** The previous complete query of the same window on this device, for re-query comparison. */
data class PreviousQuery(
    val queriedAt: Instant,
    val maskedIds: Set<String>,
    val sampleCount: Int,
)

data class SessionProbeInput(
    val label: String,
    val window: SessionWindow,
    val zone: ZoneId,
    val queriedAt: Instant,
    val heartRate: ReadResult<ObservedHeartRateRecord>,
    val exerciseSessions: ReadResult<ObservedExerciseSession>,
    val steps: ReadResult<StepsObservation>,
    val previous: PreviousQuery?,
)

data class ChangesObservation(
    val outcome: ReadOutcome,
    val issuedAt: Instant?,
    val queriedAt: Instant,
    val tokenExpired: Boolean,
    val upsertions: Int,
    val deletionMaskedIds: List<String>,
    val deletionsMatchingLastQuery: Int,
    val pages: Int,
    val hasMoreRemaining: Boolean,
    val failureType: String?,
)

data class ProbeEnvironment(
    val androidSdkInt: Int,
    val phoneManufacturer: String,
    val phoneModel: String,
    val healthConnectStatus: ReadOutcome?,
    val packageVersions: Map<String, String?>,
    val grantedPermissions: Set<String>,
    val requestedPermissions: Set<String>,
)

object ProbeLabels {
    fun recordingMethod(value: Int): String = when (value) {
        0 -> "UNKNOWN"
        1 -> "ACTIVELY_RECORDED"
        2 -> "AUTOMATICALLY_RECORDED"
        3 -> "MANUAL_ENTRY"
        else -> "OTHER($value)"
    }

    fun deviceType(value: Int?): String = when (value) {
        null -> "device 없음"
        0 -> "UNKNOWN"
        1 -> "WATCH"
        2 -> "PHONE"
        3 -> "SCALE"
        4 -> "RING"
        5 -> "HEAD_MOUNTED"
        6 -> "FITNESS_BAND"
        7 -> "CHEST_STRAP"
        8 -> "SMART_DISPLAY"
        else -> "TYPE($value)"
    }

    fun outcome(value: ReadOutcome?): String = value?.name ?: "AVAILABLE"
}

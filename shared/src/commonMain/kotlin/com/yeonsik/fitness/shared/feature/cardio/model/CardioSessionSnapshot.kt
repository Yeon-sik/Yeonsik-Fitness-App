package com.yeonsik.fitness.shared.feature.cardio.model

data class CardioSessionSnapshot @JvmOverloads constructor(
    val recordId: String,
    val activityId: String,
    val activityLabel: String,
    val status: String,
    val startedAtEpochMillis: Long,
    val lastResumedAtEpochMillis: Long?,
    val activeDurationMillis: Long,
    val distanceMeters: Double,
    val acceptedPointCount: Int,
    val gpsStatus: String,
    val averageHeartRateBpm: Double?,
    val environment: CardioEnvironment = CardioEnvironment.OUTDOOR,
    val manualDistanceMeters: Double? = null
) {
    val activityType: CardioActivityType get() = CardioActivityType.fromId(activityId)
    val usesGps: Boolean get() = activityType.usesGps(environment)
    val canInputManualDistance: Boolean
        get() = !usesGps && activityType.supportsManualDistance
    val recordedDistanceMeters: Double?
        get() = if (usesGps) distanceMeters else manualDistanceMeters

    fun elapsedSeconds(nowEpochMillis: Long): Int {
        val activeMillis = activeDurationMillis + if (status == STATUS_TRACKING
            && lastResumedAtEpochMillis != null) {
            (nowEpochMillis - lastResumedAtEpochMillis).coerceAtLeast(0L)
        } else 0L
        return (activeMillis / 1_000L).toInt()
    }

    companion object {
        const val STATUS_TRACKING = "tracking"
        const val STATUS_PAUSED = "paused"
        const val STATUS_COMPLETED = "completed"
    }
}

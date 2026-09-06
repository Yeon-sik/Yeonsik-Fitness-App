package com.yeonsik.fitnessapp.feature.cardio.model

data class CardioSessionSnapshot(
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
    val averageHeartRateBpm: Double?
) {
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

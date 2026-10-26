package com.yeonsik.fitnessapp.feature.workout.api

data class RestTimerSchedule(val ownerId: String, val endsAtMillis: Long, val totalSeconds: Int)

/** Durable timer and single-consumer completion boundary; no Android types reach the ViewModel. */
interface RestTimerAlarmScheduler {
    fun schedule(timer: RestTimerSchedule)
    fun pendingTimer(): RestTimerSchedule?
    fun claimCompletion(ownerId: String, endsAtMillis: Long, nowMillis: Long): Boolean
    fun cancel()
}

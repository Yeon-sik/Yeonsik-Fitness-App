package com.yeonsik.fitnessapp.feature.workout.application

import com.yeonsik.fitnessapp.feature.workout.api.RestTimerSchedule

enum class RestTimerAlarmMode { EXACT_ALLOW_IDLE, INEXACT_ALLOW_IDLE }

object RestTimerNotificationPolicy {
    fun alarmMode(apiLevel: Int, exactAllowed: Boolean): RestTimerAlarmMode =
        if (apiLevel < 31 || exactAllowed) RestTimerAlarmMode.EXACT_ALLOW_IDLE else RestTimerAlarmMode.INEXACT_ALLOW_IDLE

    fun shouldNotify(foreground: Boolean, notificationPermission: Boolean, notificationsEnabled: Boolean): Boolean =
        !foreground && notificationPermission && notificationsEnabled

    fun canClaim(current: RestTimerSchedule?, ownerId: String, endsAtMillis: Long, nowMillis: Long): Boolean =
        current != null && current.ownerId == ownerId && current.endsAtMillis == endsAtMillis && nowMillis >= endsAtMillis
}

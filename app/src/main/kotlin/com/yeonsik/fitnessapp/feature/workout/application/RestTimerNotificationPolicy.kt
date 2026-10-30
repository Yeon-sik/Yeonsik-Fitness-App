package com.yeonsik.fitnessapp.feature.workout.application

/** How the rest end alarm is scheduled. */
enum class RestTimerAlarmMode {
    /** setExactAndAllowWhileIdle at the end time. */
    EXACT,

    /** setWindow from the end time: Android's fallback when exact alarms are not allowed. */
    WINDOWED
}

/** Rest end notification decisions. Plain values only, so they are JVM-testable. */
object RestTimerNotificationPolicy {
    /** How late a [RestTimerAlarmMode.WINDOWED] alarm may fire. */
    const val ALARM_WINDOW_MILLIS = 10_000L

    private const val API_EXACT_ALARM_PERMISSION = 31 // Android 12 (S)
    private const val API_NOTIFICATION_PERMISSION = 33 // Android 13 (TIRAMISU)

    /** In the foreground the app-level toast announces the end; the system notification does not. */
    fun shouldPost(appInForeground: Boolean, permissionGranted: Boolean, notificationsEnabled: Boolean): Boolean =
        !appInForeground && permissionGranted && notificationsEnabled

    /** Before Android 12 exact alarms need no permission. */
    fun alarmMode(sdkInt: Int, canScheduleExactAlarms: Boolean): RestTimerAlarmMode =
        if (sdkInt < API_EXACT_ALARM_PERMISSION || canScheduleExactAlarms) RestTimerAlarmMode.EXACT
        else RestTimerAlarmMode.WINDOWED

    /** Notification permission is a runtime permission from Android 13; ask once only. */
    fun shouldRequestPermission(sdkInt: Int, permissionGranted: Boolean, alreadyRequested: Boolean): Boolean =
        sdkInt >= API_NOTIFICATION_PERMISSION && !permissionGranted && !alreadyRequested

    /** Below Android 13 posting needs no runtime permission. */
    fun permissionGranted(sdkInt: Int, runtimePermissionGranted: Boolean): Boolean =
        sdkInt < API_NOTIFICATION_PERMISSION || runtimePermissionGranted
}

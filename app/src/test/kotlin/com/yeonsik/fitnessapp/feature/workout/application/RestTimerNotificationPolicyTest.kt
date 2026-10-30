package com.yeonsik.fitnessapp.feature.workout.application

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RestTimerNotificationPolicyTest {
    @Test
    fun foregroundNeverPostsBecauseTheAppToastsInstead() {
        assertFalse(RestTimerNotificationPolicy.shouldPost(appInForeground = true, permissionGranted = true, notificationsEnabled = true))
    }

    @Test
    fun backgroundPostsOnlyWithPermissionAndEnabledNotifications() {
        assertTrue(RestTimerNotificationPolicy.shouldPost(appInForeground = false, permissionGranted = true, notificationsEnabled = true))
        assertFalse(RestTimerNotificationPolicy.shouldPost(appInForeground = false, permissionGranted = false, notificationsEnabled = true))
        assertFalse(RestTimerNotificationPolicy.shouldPost(appInForeground = false, permissionGranted = true, notificationsEnabled = false))
    }

    @Test
    fun exactAlarmsBeforeAndroid12AndWhenAllowed() {
        assertEquals(RestTimerAlarmMode.EXACT, RestTimerNotificationPolicy.alarmMode(30, canScheduleExactAlarms = false))
        assertEquals(RestTimerAlarmMode.EXACT, RestTimerNotificationPolicy.alarmMode(34, canScheduleExactAlarms = true))
    }

    @Test
    fun windowedAlarmWhenExactAlarmsAreNotAllowed() {
        assertEquals(RestTimerAlarmMode.WINDOWED, RestTimerNotificationPolicy.alarmMode(31, canScheduleExactAlarms = false))
        assertEquals(RestTimerAlarmMode.WINDOWED, RestTimerNotificationPolicy.alarmMode(34, canScheduleExactAlarms = false))
        assertEquals(10_000L, RestTimerNotificationPolicy.ALARM_WINDOW_MILLIS)
    }

    @Test
    fun permissionIsRequestedOnceFromAndroid13() {
        assertTrue(RestTimerNotificationPolicy.shouldRequestPermission(33, permissionGranted = false, alreadyRequested = false))
        assertFalse(RestTimerNotificationPolicy.shouldRequestPermission(33, permissionGranted = false, alreadyRequested = true))
        assertFalse(RestTimerNotificationPolicy.shouldRequestPermission(34, permissionGranted = true, alreadyRequested = false))
        assertFalse(RestTimerNotificationPolicy.shouldRequestPermission(32, permissionGranted = false, alreadyRequested = false))
    }

    @Test
    fun postingNeedsNoRuntimePermissionBeforeAndroid13() {
        assertTrue(RestTimerNotificationPolicy.permissionGranted(32, runtimePermissionGranted = false))
        assertFalse(RestTimerNotificationPolicy.permissionGranted(33, runtimePermissionGranted = false))
        assertTrue(RestTimerNotificationPolicy.permissionGranted(33, runtimePermissionGranted = true))
    }
}

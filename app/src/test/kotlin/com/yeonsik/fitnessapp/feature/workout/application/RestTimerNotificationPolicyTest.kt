package com.yeonsik.fitnessapp.feature.workout.application

import com.yeonsik.fitnessapp.feature.workout.api.RestTimerSchedule
import org.junit.Assert.*
import org.junit.Test

class RestTimerNotificationPolicyTest {
    @Test fun foregroundUsesOnlyTheAppHost() {
        assertFalse(RestTimerNotificationPolicy.shouldNotify(true, true, true))
        assertTrue(RestTimerNotificationPolicy.shouldNotify(false, true, true))
    }

    @Test fun deniedPermissionOrDisabledNotificationsNeverPost() {
        assertFalse(RestTimerNotificationPolicy.shouldNotify(false, false, true))
        assertFalse(RestTimerNotificationPolicy.shouldNotify(false, true, false))
    }

    @Test fun exactPermissionSelectsTheAvailableAlarmMode() {
        assertEquals(RestTimerAlarmMode.EXACT_ALLOW_IDLE, RestTimerNotificationPolicy.alarmMode(30, false))
        assertEquals(RestTimerAlarmMode.EXACT_ALLOW_IDLE, RestTimerNotificationPolicy.alarmMode(31, true))
        assertEquals(RestTimerAlarmMode.INEXACT_ALLOW_IDLE, RestTimerNotificationPolicy.alarmMode(31, false))
        assertEquals(RestTimerAlarmMode.INEXACT_ALLOW_IDLE, RestTimerNotificationPolicy.alarmMode(36, false))
    }

    @Test fun staleAlarmCannotCompleteAReplacementOrAnotherOwnersTimer() {
        val current = RestTimerSchedule("new-owner", 5_000, 90)
        assertFalse(RestTimerNotificationPolicy.canClaim(current, "old-owner", 5_000, 6_000))
        assertFalse(RestTimerNotificationPolicy.canClaim(current, "new-owner", 4_000, 6_000))
        assertFalse(RestTimerNotificationPolicy.canClaim(current, "new-owner", 5_000, 4_999))
        assertTrue(RestTimerNotificationPolicy.canClaim(current, "new-owner", 5_000, 5_000))
    }

    @Test fun cancellationOrAnEarlierConsumerPreventsAnotherCompletion() {
        var pending: RestTimerSchedule? = RestTimerSchedule("owner", 1_000, 90)
        assertTrue(RestTimerNotificationPolicy.canClaim(pending, "owner", 1_000, 1_000))
        pending = null
        assertFalse(RestTimerNotificationPolicy.canClaim(pending, "owner", 1_000, 1_000))
    }
}

package com.yeonsik.fitnessapp.feature.workout.api

/**
 * Schedules the system alarm that announces a rest end while the app is in the background.
 * There is one rest alarm: scheduling replaces the previous one.
 */
interface RestTimerAlarmScheduler {
    fun schedule(endsAtMillis: Long)

    /** Cancels the pending alarm and removes a rest notification that is already showing. */
    fun cancel()

    companion object {
        /** No alarms, for hosts without a platform scheduler. */
        @JvmField
        val NONE: RestTimerAlarmScheduler = object : RestTimerAlarmScheduler {
            override fun schedule(endsAtMillis: Long) = Unit
            override fun cancel() = Unit
        }
    }
}

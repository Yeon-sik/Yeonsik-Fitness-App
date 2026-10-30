package com.yeonsik.fitnessapp.feature.workout.application

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.yeonsik.fitnessapp.feature.workout.api.RestTimerAlarmScheduler

/**
 * AlarmManager-backed rest alarm. One fixed PendingIntent, so a new rest replaces the old alarm.
 * Without exact alarm access it falls back to a 10s window, as Android recommends.
 */
class AndroidRestTimerAlarmScheduler(context: Context) : RestTimerAlarmScheduler {
    private val appContext = context.applicationContext
    private val alarmManager = appContext.getSystemService(AlarmManager::class.java)

    override fun schedule(endsAtMillis: Long) {
        val manager = alarmManager ?: return
        val canScheduleExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()
        val pending = alarmIntent()
        when (RestTimerNotificationPolicy.alarmMode(Build.VERSION.SDK_INT, canScheduleExact)) {
            RestTimerAlarmMode.EXACT -> try {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endsAtMillis, pending)
            } catch (_: SecurityException) {
                // Exact alarm access was revoked after the check.
                setWindow(manager, endsAtMillis, pending)
            }
            RestTimerAlarmMode.WINDOWED -> setWindow(manager, endsAtMillis, pending)
        }
    }

    override fun cancel() {
        alarmManager?.cancel(alarmIntent())
        RestTimerNotifications.cancel(appContext)
    }

    private fun setWindow(manager: AlarmManager, endsAtMillis: Long, pending: PendingIntent) {
        manager.setWindow(
            AlarmManager.RTC_WAKEUP, endsAtMillis, RestTimerNotificationPolicy.ALARM_WINDOW_MILLIS, pending
        )
    }

    private fun alarmIntent(): PendingIntent = PendingIntent.getBroadcast(
        appContext,
        ALARM_REQUEST_CODE,
        Intent(appContext, RestTimerAlarmReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private companion object {
        const val ALARM_REQUEST_CODE = 4202
    }
}

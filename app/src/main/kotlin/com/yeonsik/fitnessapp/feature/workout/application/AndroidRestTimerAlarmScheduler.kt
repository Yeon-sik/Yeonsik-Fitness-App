package com.yeonsik.fitnessapp.feature.workout.application

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.yeonsik.fitnessapp.feature.workout.api.RestTimerAlarmScheduler
import com.yeonsik.fitnessapp.feature.workout.api.RestTimerSchedule

class AndroidRestTimerAlarmScheduler(context: Context) : RestTimerAlarmScheduler {
    private val context = context.applicationContext
    private val preferences = this.context.getSharedPreferences("fitness_rest_timer", Context.MODE_PRIVATE)
    private val alarms = this.context.getSystemService(AlarmManager::class.java)

    override fun schedule(timer: RestTimerSchedule) = synchronized(lock) {
        require(timer.ownerId.isNotBlank() && timer.totalSeconds > 0 && timer.endsAtMillis > 0)
        RestTimerNotifications.cancel(context)
        check(preferences.edit().putString("owner", timer.ownerId).putLong("ends_at", timer.endsAtMillis)
            .putInt("seconds", timer.totalSeconds).commit()) { "휴식 타이머를 저장하지 못했습니다." }
        val intent = Intent(context, RestTimerAlarmReceiver::class.java).setAction(ACTION_FINISHED)
            .putExtra(EXTRA_OWNER, timer.ownerId).putExtra(EXTRA_END, timer.endsAtMillis)
        val pending = PendingIntent.getBroadcast(context, REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val exactAllowed = Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()
        try {
            if (RestTimerNotificationPolicy.alarmMode(Build.VERSION.SDK_INT, exactAllowed) == RestTimerAlarmMode.EXACT_ALLOW_IDLE) {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timer.endsAtMillis, pending)
            } else {
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timer.endsAtMillis, pending)
            }
        } catch (_: SecurityException) {
            // Special access can be revoked between the check and scheduling.
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timer.endsAtMillis, pending)
        }
    }

    override fun pendingTimer(): RestTimerSchedule? = synchronized(lock) {
        val owner = preferences.getString("owner", null) ?: return@synchronized null
        val end = preferences.getLong("ends_at", 0)
        val seconds = preferences.getInt("seconds", 0)
        if (owner.isBlank() || end <= 0 || seconds <= 0) null else RestTimerSchedule(owner, end, seconds)
    }

    override fun claimCompletion(ownerId: String, endsAtMillis: Long, nowMillis: Long): Boolean = synchronized(lock) {
        if (!RestTimerNotificationPolicy.canClaim(pendingTimer(), ownerId, endsAtMillis, nowMillis)) return@synchronized false
        if (!clearStored()) return@synchronized false
        cancelAlarm()
        true
    }

    override fun cancel() = synchronized(lock) {
        clearStored()
        cancelAlarm()
        RestTimerNotifications.cancel(context)
    }

    fun cancelForOtherOwner(ownerId: String) {
        synchronized(lock) { if (pendingTimer()?.ownerId?.let { it != ownerId } == true) cancel() }
    }

    private fun clearStored(): Boolean = preferences.edit().remove("owner").remove("ends_at").remove("seconds").commit()

    private fun cancelAlarm() {
        val intent = Intent(context, RestTimerAlarmReceiver::class.java).setAction(ACTION_FINISHED)
        PendingIntent.getBroadcast(context, REQUEST_CODE, intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)?.let {
            alarms.cancel(it)
            it.cancel()
        }
    }

    companion object {
        private val lock = Any()
        private const val REQUEST_CODE = 4201
        internal const val ACTION_FINISHED = "com.yeonsik.fitnessapp.REST_TIMER_FINISHED"
        internal const val EXTRA_OWNER = "owner"
        internal const val EXTRA_END = "ends_at"
    }
}

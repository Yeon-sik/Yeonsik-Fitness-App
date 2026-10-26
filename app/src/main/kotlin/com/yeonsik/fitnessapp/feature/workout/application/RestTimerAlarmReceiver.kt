package com.yeonsik.fitnessapp.feature.workout.application

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class RestTimerAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AndroidRestTimerAlarmScheduler.ACTION_FINISHED) return
        val owner = intent.getStringExtra(AndroidRestTimerAlarmScheduler.EXTRA_OWNER) ?: return
        val end = intent.getLongExtra(AndroidRestTimerAlarmScheduler.EXTRA_END, 0)
        deliverIfBackground(context, owner, end)
    }

    companion object {
        internal fun deliverIfBackground(context: Context, ownerId: String? = null, endsAt: Long? = null) {
            if (RestTimerAppVisibility.isForeground) return
            val scheduler = AndroidRestTimerAlarmScheduler(context)
            val timer = scheduler.pendingTimer() ?: return
            if (ownerId != null && (timer.ownerId != ownerId || timer.endsAtMillis != endsAt)) return
            if (scheduler.claimCompletion(timer.ownerId, timer.endsAtMillis, System.currentTimeMillis())) {
                RestTimerNotifications.postIfAllowed(context)
            }
        }
    }
}

/** MainActivity is the only activity; count starts/stops without ProcessLifecycleOwner's stop delay. */
object RestTimerAppVisibility {
    private var startedActivities = 0
    val isForeground: Boolean get() = startedActivities > 0

    @JvmStatic fun onStarted(context: Context) {
        startedActivities++
        RestTimerNotifications.cancel(context)
    }

    @JvmStatic fun onStopped(context: Context) {
        startedActivities = (startedActivities - 1).coerceAtLeast(0)
        // Covers an alarm delivered just before onStop, when it correctly yielded to the foreground host.
        if (!isForeground) RestTimerAlarmReceiver.deliverIfBackground(context)
    }
}

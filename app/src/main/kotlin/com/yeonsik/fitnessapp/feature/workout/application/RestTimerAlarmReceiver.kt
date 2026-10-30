package com.yeonsik.fitnessapp.feature.workout.application

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner

/**
 * Receives the rest end alarm. In the foreground the app-level end check toasts instead. When
 * the process was killed this runs in a fresh process, which is in the background, so it posts.
 */
class RestTimerAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val foreground = ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        val post = RestTimerNotificationPolicy.shouldPost(
            appInForeground = foreground,
            permissionGranted = RestTimerNotifications.permissionGranted(context),
            notificationsEnabled = RestTimerNotifications.notificationsEnabled(context)
        )
        if (post) RestTimerNotifications.post(context)
    }
}

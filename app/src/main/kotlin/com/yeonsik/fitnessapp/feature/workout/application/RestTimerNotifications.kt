package com.yeonsik.fitnessapp.feature.workout.application

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.yeonsik.fitnessapp.MainActivity
import com.yeonsik.fitnessapp.R

/**
 * The rest end notification: one channel and at most one notification. It carries no account
 * or workout details.
 */
object RestTimerNotifications {
    const val CHANNEL_ID = "rest_timer"
    const val NOTIFICATION_ID = 4201
    private const val CONTENT_REQUEST_CODE = 4201
    private const val PREFERENCES_NAME = "rest_timer_notifications"
    private const val KEY_PERMISSION_REQUESTED = "rest_timer_notification_permission_requested"

    /** Creating an existing channel is a no-op, so this runs at app start and before posting. */
    @JvmStatic
    fun ensureChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL_ID, "휴식 타이머", NotificationManager.IMPORTANCE_HIGH)
            .apply { description = "휴식이 끝나면 알려줍니다" }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    @JvmStatic
    fun permissionGranted(context: Context): Boolean = RestTimerNotificationPolicy.permissionGranted(
        Build.VERSION.SDK_INT,
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    )

    @JvmStatic
    fun notificationsEnabled(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    /** Callers check [RestTimerNotificationPolicy.shouldPost] first. */
    fun post(context: Context) {
        ensureChannel(context)
        val open = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val contentIntent = PendingIntent.getActivity(
            context, CONTENT_REQUEST_CODE, open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle("휴식 종료")
            .setContentText("다음 세트를 시작하세요.")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // Permission revoked between the check and the post: stay silent.
        }
    }

    @JvmStatic
    fun cancel(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    @JvmStatic
    fun permissionRequested(context: Context): Boolean =
        preferences(context).getBoolean(KEY_PERMISSION_REQUESTED, false)

    @JvmStatic
    fun markPermissionRequested(context: Context) {
        preferences(context).edit().putBoolean(KEY_PERMISSION_REQUESTED, true).apply()
    }

    private fun preferences(context: Context) =
        context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
}

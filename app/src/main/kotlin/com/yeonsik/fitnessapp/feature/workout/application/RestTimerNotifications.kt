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

object RestTimerNotifications {
    private const val CHANNEL = "rest_timer"
    private const val NOTIFICATION_ID = 4202

    @JvmStatic fun ensureChannel(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "휴식 타이머", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "휴식이 끝나면 알려줍니다"
            }
        )
    }

    fun postIfAllowed(context: Context) {
        ensureChannel(context)
        val permission = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context,
            Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val manager = NotificationManagerCompat.from(context)
        val enabled = manager.areNotificationsEnabled() && context.getSystemService(NotificationManager::class.java)
            .getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE
        if (!RestTimerNotificationPolicy.shouldNotify(RestTimerAppVisibility.isForeground, permission, enabled)) return
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle("휴식 종료").setContentText("다음 세트를 시작하세요.")
            .setCategory(NotificationCompat.CATEGORY_ALARM).setAutoCancel(true)
            .setContentIntent(PendingIntent.getActivity(context, NOTIFICATION_ID, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            .build()
        try { manager.notify(NOTIFICATION_ID, notification) } catch (_: SecurityException) { /* Permission was revoked. */ }
    }

    @JvmStatic fun cancel(context: Context) { NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID) }
}

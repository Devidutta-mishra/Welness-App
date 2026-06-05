package com.example.yourswelnes.core.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.yourswelnes.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

@Singleton
class AppNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val notificationManager: NotificationManagerCompat
) {

    fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "App Notifications",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Alerts and announcements from the server"
        }
        notificationManager.createNotificationChannel(channel)
    }

    /**
     * Posts a system-tray notification for the given notification item. Tapping it opens
     * MainActivity with [ACTION_OPEN_NOTIFICATIONS] so the app navigates to the list and
     * immediately marks the notification read.
     */
    fun show(notificationId: Int, title: String, message: String) {
        val tapIntent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN_NOTIFICATIONS
            putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setDefaults(NotificationCompat.DEFAULT_SOUND)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            notificationManager.notify(notificationId, notification)
            Timber.tag(TAG).d("System notification posted: id=$notificationId, title=$title")
        } catch (_: SecurityException) {
            Timber.tag(TAG).w("POST_NOTIFICATIONS permission not granted — skipping system notification id=$notificationId")
        }
    }

    companion object {
        const val CHANNEL_ID = "app_notifications"
        const val ACTION_OPEN_NOTIFICATIONS = "com.example.yourswelnes.OPEN_NOTIFICATIONS"
        const val EXTRA_NOTIFICATION_ID = "notification_id"
        private const val TAG = "AppNotificationManager"
    }
}

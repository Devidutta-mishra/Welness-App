package com.example.yourswelnes.core.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocationNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val notificationManager: NotificationManagerCompat,
    private val appNotificationManager: AppNotificationManager
) {
    fun createChannels() {
        val serviceChannel = NotificationChannel(
            CHANNEL_ID_SERVICE,
            "Location Tracking",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shown while location tracking is active"
            setShowBadge(false)
        }

        val gpsAlertChannel = NotificationChannel(
            CHANNEL_ID_GPS_ALERT,
            "GPS Alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Alert when GPS is disabled during the tracking window"
        }

        notificationManager.createNotificationChannel(serviceChannel)
        notificationManager.createNotificationChannel(gpsAlertChannel)

        appNotificationManager.createChannel()
    }

    fun buildServiceNotification() =
        NotificationCompat.Builder(context, CHANNEL_ID_SERVICE)
            .setContentTitle("Location Tracking Active")
            .setContentText("Monitoring attendance location")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .setSilent(true)
            .build()

    fun showGpsDisabledNotification() {
        val intent = Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_GPS_ALERT)
            .setContentTitle("Location Tracking Required")
            .setContentText("Please enable location services to continue attendance tracking.")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)  // heads-up only on first show, not every 30s update
            .build()

        try {
            notificationManager.notify(NOTIFICATION_ID_GPS_ALERT, notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS permission not granted — silently ignore
        }
    }

    fun cancelGpsDisabledNotification() {
        notificationManager.cancel(NOTIFICATION_ID_GPS_ALERT)
    }

    companion object {
        const val CHANNEL_ID_SERVICE = "location_tracking_service"
        const val CHANNEL_ID_GPS_ALERT = "gps_alert"
        const val NOTIFICATION_ID_SERVICE = 1001
        const val NOTIFICATION_ID_GPS_ALERT = 1002
    }
}

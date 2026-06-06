package com.example.yourswelnes.core.notification

import com.example.yourswelnes.core.datastore.AuthPreferencesDataStore
import com.example.yourswelnes.core.datastore.FcmPreferencesDataStore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber

@AndroidEntryPoint
class YwFirebaseMessagingService : FirebaseMessagingService() {

    @Inject lateinit var fcmPrefs: FcmPreferencesDataStore
    @Inject lateinit var authPrefs: AuthPreferencesDataStore
    @Inject lateinit var appNotificationManager: AppNotificationManager

    override fun onNewToken(token: String) {
        Timber.tag(TAG).i("FCM TOKEN GENERATED: $token")
        // Detached scope: token saves must survive even if the service is torn down immediately
        // after onNewToken() returns.
        CoroutineScope(Dispatchers.IO).launch {
            fcmPrefs.saveFcmToken(token)
            Timber.tag(TAG).i("FCM TOKEN REFRESHED: saved to local store")
        }
    }

    /**
     * Called when a message arrives in the FOREGROUND, and for data-bearing messages in the
     * BACKGROUND as well. The backend sends a data-heavy payload, so this is the primary path.
     *
     * IMPORTANT: notification_id is OPTIONAL. The backend does not always include it in the FCM
     * data block (verified on-device: pushes arrived with no notification_id). We therefore never
     * reject a message for lacking it — we display using whatever title/body is present and use
     * the FCM messageId as the dedup key when notification_id is absent.
     */
    override fun onMessageReceived(message: RemoteMessage) {
        Timber.tag(TAG).i("MESSAGE RECEIVED: messageId=${message.messageId}")
        // Full payload dump — proves the exact shape the backend sends, in every app state.
        Timber.tag(TAG).i("  notification.title=${message.notification?.title}")
        Timber.tag(TAG).i("  notification.body=${message.notification?.body}")
        Timber.tag(TAG).i("  notification.channelId=${message.notification?.channelId}")
        if (message.data.isEmpty()) {
            Timber.tag(TAG).i("  data={} (empty)")
        } else {
            message.data.forEach { (k, v) -> Timber.tag(TAG).i("  data[$k]=$v") }
        }

        // notification_id is optional. Resolve title/body from the notification block first,
        // then fall back to the most common data keys the backend might use.
        val notificationId = message.data["notification_id"]?.takeIf { it.isNotBlank() }
        val title = message.notification?.title
            ?: message.data["title"]
            ?: message.data["notification_title"]
            ?: "YW Center"
        val body = message.notification?.body
            ?: message.data["body"]
            ?: message.data["message"]
            ?: message.data["notification_body"]
            ?: ""

        // Dedup key: prefer the stable backend id; otherwise use the FCM-guaranteed messageId.
        val dedupKey = notificationId ?: message.messageId ?: return

        // Detached scope — intentionally NOT tied to any parent job.
        // Firebase calls stopSelf() after onMessageReceived() returns, which triggers
        // onDestroy(). A service-scoped coroutine (even with NonCancellable) is cancelled
        // before it starts if the scope's Job is cancelled before the coroutine is scheduled.
        // A standalone CoroutineScope has no parent to cancel it; it runs to completion.
        CoroutineScope(Dispatchers.IO).launch {
            if (!authPrefs.isLoggedIn()) {
                Timber.tag(TAG).w("No user logged in — suppressing notification key=$dedupKey")
                return@launch
            }

            if (fcmPrefs.isNotificationShown(dedupKey)) {
                Timber.tag(TAG).d("DUPLICATE PUSH suppressed: key=$dedupKey")
                return@launch
            }
            fcmPrefs.markNotificationShown(dedupKey)

            // Tray id: notification_id when numeric (lets a re-push update the same tray entry),
            // otherwise a stable hash of the dedup key.
            val trayId = notificationId?.toIntOrNull() ?: dedupKey.hashCode()
            appNotificationManager.show(trayId, title, body, notificationId?.toIntOrNull())

            Timber.tag(TAG).i("NOTIFICATION CREATED: key=$dedupKey, title=$title")
            Timber.tag(TAG).i("NOTIFICATION DISPLAYED: key=$dedupKey")
        }
    }

    companion object {
        private const val TAG = "YwFCM"
    }
}

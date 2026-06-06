package com.example.yourswelnes.core.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.yourswelnes.feature.notifications.data.NotificationRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit
import timber.log.Timber

/**
 * Fallback for devices where FCM push delivery is blocked (OEM battery optimizers, etc.).
 * Runs every 15 minutes when network is available, fetches the latest notifications from the
 * server, and shows a system-tray notification for any entry not yet displayed.
 *
 * The [NotificationRepository.fetchNotifications] call handles the isDisplayed guard
 * so no notification is shown twice.
 */
@HiltWorker
class NotificationSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val notificationRepository: NotificationRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Timber.tag(TAG).d("Polling for new notifications")
        return notificationRepository.fetchNotifications()
            .fold(
                onSuccess = { Result.success() },
                onFailure = {
                    Timber.tag(TAG).w("Notification sync failed — will retry: ${it.message}")
                    Result.retry()
                }
            )
    }

    companion object {
        private const val TAG = "NotificationSyncWorker"
        private const val WORK_NAME = "notification_sync_periodic"

        fun schedule(workManager: WorkManager) {
            val request = PeriodicWorkRequestBuilder<NotificationSyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            workManager.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
            Timber.tag(TAG).d("Notification sync worker scheduled (15 min interval)")
        }

        fun cancel(workManager: WorkManager) {
            workManager.cancelUniqueWork(WORK_NAME)
        }
    }
}

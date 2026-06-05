package com.example.yourswelnes.core.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.yourswelnes.feature.location.data.repository.LocationConfigRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit
import timber.log.Timber

/**
 * Runs every 30 minutes (when network is available) to keep the local tracking schedule
 * in sync with the server. The location collection service reads ONLY from the local cache,
 * so tracking continues uninterrupted even while this worker is idle or the network is down.
 */
@HiltWorker
class ScheduleSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val locationConfigRepository: LocationConfigRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Timber.tag(TAG).d("Schedule sync worker started")
        // Logging and caching are handled inside getLocationConfig().
        // This worker always returns success so WorkManager does not apply backoff
        // unnecessarily — the next periodic run will retry automatically.
        locationConfigRepository.getLocationConfig()
        return Result.success()
    }

    companion object {
        private const val TAG = "ScheduleSyncWorker"
        private const val WORK_NAME = "schedule_sync_periodic"

        fun schedule(workManager: WorkManager) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<ScheduleSyncWorker>(30, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
                .build()

            workManager.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
            Timber.tag(TAG).d("Schedule sync worker enqueued (30 min interval, network required)")
        }

        fun cancel(workManager: WorkManager) {
            workManager.cancelUniqueWork(WORK_NAME)
        }
    }
}

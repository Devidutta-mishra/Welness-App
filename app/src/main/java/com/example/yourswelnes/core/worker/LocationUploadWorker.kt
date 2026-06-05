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
import com.example.yourswelnes.core.location.LocationUploader
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit
import timber.log.Timber

@HiltWorker
class LocationUploadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val locationUploader: LocationUploader
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Timber.tag(TAG).d("doWork started — draining pending locations")
        return when (locationUploader.uploadPending()) {
            LocationUploader.Result.SUCCESS,
            LocationUploader.Result.NOTHING_TO_DO -> Result.success()
            LocationUploader.Result.FAILED -> Result.retry()
        }
    }

    companion object {
        private const val TAG = "LocationUploadWorker"
        private const val WORK_NAME = "location_upload_periodic"

        fun cancel(workManager: WorkManager) {
            workManager.cancelUniqueWork(WORK_NAME)
            Timber.tag(TAG).d("Periodic upload worker cancelled")
        }

        fun schedule(workManager: WorkManager) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            // WorkManager minimum periodic interval is 15 min; backend's uploadIntervalMinutes
            // may be lower (e.g. 10 min) but cannot go below the platform floor.
            val request = PeriodicWorkRequestBuilder<LocationUploadWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .build()

            workManager.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
            Timber.tag(TAG).d("Scheduled periodic upload worker (15 min interval, network required)")
        }
    }
}

package com.example.yourswelnes.core.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.yourswelnes.feature.monitoring.data.AppMonitoringRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit
import timber.log.Timber

@HiltWorker
class AppInstallationSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val appMonitoringRepository: AppMonitoringRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Timber.d("AppInstallationSyncWorker started")
        return appMonitoringRepository.syncApps()
            .fold(
                onSuccess = {
                    Timber.d("AppInstallationSyncWorker completed")
                    Result.success()
                },
                onFailure = { error ->
                    Timber.e(error, "AppInstallationSyncWorker failed — will retry")
                    Result.retry()
                }
            )
    }

    companion object {
        private const val WORK_NAME_PERIODIC = "app_installation_sync_periodic"
        private const val WORK_NAME_ONCE = "app_installation_sync_once"

        private val networkConstraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        /** Scheduled once from Application.onCreate — runs every 6 hours. */
        fun schedulePeriodic(workManager: WorkManager) {
            val request = PeriodicWorkRequestBuilder<AppInstallationSyncWorker>(6, TimeUnit.HOURS)
                .setConstraints(networkConstraints)
                .build()

            workManager.enqueueUniquePeriodicWork(
                WORK_NAME_PERIODIC,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
            Timber.d("AppInstallationSyncWorker periodic scheduled (6h)")
        }

        /** Fires once immediately on app launch. KEEP policy avoids duplicate runs. */
        fun cancel(workManager: WorkManager) {
            workManager.cancelUniqueWork(WORK_NAME_PERIODIC)
            workManager.cancelUniqueWork(WORK_NAME_ONCE)
            Timber.d("AppInstallationSyncWorker cancelled")
        }

        fun scheduleOneTime(workManager: WorkManager) {
            val request = OneTimeWorkRequestBuilder<AppInstallationSyncWorker>()
                .setConstraints(networkConstraints)
                .build()

            workManager.enqueueUniqueWork(
                WORK_NAME_ONCE,
                ExistingWorkPolicy.KEEP,
                request
            )
            Timber.d("AppInstallationSyncWorker one-time scheduled")
        }
    }
}

package com.example.yourswelnes.core.worker

import android.content.Context
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.yourswelnes.core.datastore.AuthPreferencesDataStore
import com.example.yourswelnes.core.datastore.LocationPreferencesDataStore
import com.example.yourswelnes.core.location.LocationScheduler
import com.example.yourswelnes.core.location.LocationServiceState
import com.example.yourswelnes.core.service.LocationForegroundService
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit
import timber.log.Timber

/**
 * Offline-capable watchdog that restarts [LocationForegroundService] when it has been killed
 * while the tracking window is active.
 *
 * This worker has NO network constraint — it runs every 15 minutes regardless of internet
 * availability. That is the critical property: all other workers in this app require
 * connectivity, so when internet is off and the foreground service is killed by the OS, this
 * is the only mechanism that can revive collection without user interaction.
 *
 * Android 12+ FSSA: startForegroundService() from a background context requires the app to be
 * exempt from battery optimisation (isIgnoringBatteryOptimizations == true). The onboarding
 * flow already enforces this exemption — once granted, restarts succeed silently. Without the
 * exemption a ForegroundServiceStartNotAllowedException is caught and logged; the
 * LocationPermissionScreen will guide the user to grant it on the next app open.
 *
 * Collection path is separate from upload: locations are saved to Room locally and uploaded
 * by LocationUploadWorker whenever connectivity returns. The watchdog intentionally does not
 * schedule uploads — it only ensures the collection service is alive.
 */
@HiltWorker
class LocationWatchdogWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val authPrefs: AuthPreferencesDataStore,
    private val locationPrefs: LocationPreferencesDataStore,
    private val locationScheduler: LocationScheduler
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Timber.tag(TAG).i("WORKER STARTED | LocationWatchdogWorker — verifying service health")
        locationPrefs.saveLastWorkerExecutionTime(System.currentTimeMillis())
        Timber.tag(TAG).i("WORKER EXECUTED | Execution timestamp saved to DataStore")

        // Gate 1: only run for authenticated users.
        if (!authPrefs.isLoggedIn()) {
            Timber.tag(TAG).d("WORKER FINISHED | No active session — watchdog idle")
            return Result.success()
        }
        Timber.tag(TAG).i("USER SESSION LOADED | Authenticated user present in DataStore")

        // Gate 2: only restart during the tracking window.
        val startTime = locationPrefs.getTrackingStartTime()
        val endTime   = locationPrefs.getTrackingEndTime()

        if (startTime == null || endTime == null) {
            Timber.tag(TAG).w(
                "TIMING CACHE LOADED | No cached timing config — " +
                "ScheduleSyncWorker will refresh it when network is available"
            )
            Timber.tag(TAG).i("WORKER FINISHED | LocationWatchdogWorker — nothing to do (no cached config)")
            return Result.success()
        }
        Timber.tag(TAG).i("TIMING CACHE LOADED | Cached tracking window: $startTime–$endTime")

        if (!locationScheduler.isInTrackingWindow(startTime, endTime)) {
            Timber.tag(TAG).d(
                "WORKER FINISHED | Outside tracking window ($startTime–$endTime) — no restart needed"
            )
            return Result.success()
        }
        Timber.tag(TAG).i("TRACKING WINDOW ACTIVE | Inside window $startTime–$endTime")

        // Gate 3: skip if service is already running in this process.
        if (LocationServiceState.isRunning.value) {
            Timber.tag(TAG).d(
                "WORKER FINISHED | LocationForegroundService is already running — watchdog idle"
            )
            return Result.success()
        }

        // Service is dead during an active window — restart it.
        Timber.tag(TAG).w(
            "WATCHDOG RESTART | LocationForegroundService is NOT running during active window " +
            "($startTime–$endTime) — restarting now"
        )
        try {
            ContextCompat.startForegroundService(
                applicationContext,
                LocationForegroundService.startIntent(applicationContext)
            )
            Timber.tag(TAG).i(
                "WATCHDOG RESTART | startForegroundService() dispatched — " +
                "collection will resume within seconds"
            )
        } catch (e: Exception) {
            // ForegroundServiceStartNotAllowedException (Android 12+) or SecurityException (OEM).
            // Both mean battery optimisation has not been disabled for this app. The onboarding
            // flow requires the exemption — guide the user there on the next app open.
            Timber.tag(TAG).e(
                e,
                "WATCHDOG RESTART | Could not start service — " +
                "battery optimization exemption required. " +
                "Next app open will prompt the user via LocationPermissionScreen."
            )
        }

        Timber.tag(TAG).i("WORKER FINISHED | LocationWatchdogWorker complete")
        return Result.success()
    }

    companion object {
        private const val TAG = "LocationWatchdog"
        const val WORK_NAME = "location_watchdog_periodic"

        /**
         * Enqueues the watchdog with NO network constraint. KEEP policy prevents rescheduling
         * from trampling an already-queued next run; use UPDATE only after a config change.
         */
        fun schedule(workManager: WorkManager) {
            val request = PeriodicWorkRequestBuilder<LocationWatchdogWorker>(15, TimeUnit.MINUTES)
                .setBackoffCriteria(BackoffPolicy.LINEAR, 5, TimeUnit.MINUTES)
                .build()

            workManager.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
            Timber.tag(TAG).d(
                "WORKER SCHEDULED | LocationWatchdogWorker registered " +
                "(15 min interval, NO network constraint — fires offline)"
            )
        }

        fun cancel(workManager: WorkManager) {
            workManager.cancelUniqueWork(WORK_NAME)
            Timber.tag(TAG).d("LocationWatchdogWorker cancelled")
        }
    }
}

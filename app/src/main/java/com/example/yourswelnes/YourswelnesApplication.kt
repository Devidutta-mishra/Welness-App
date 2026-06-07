package com.example.yourswelnes

import android.app.Application
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.WorkManager
import com.example.yourswelnes.core.datastore.AuthPreferencesDataStore
import com.example.yourswelnes.core.datastore.FcmPreferencesDataStore
import com.example.yourswelnes.core.service.LocationForegroundService
import com.example.yourswelnes.core.notification.AppNotificationManager
import com.example.yourswelnes.core.notification.LocationNotificationManager
import com.example.yourswelnes.core.worker.AppInstallationSyncWorker
import com.example.yourswelnes.core.worker.LocationUploadWorker
import com.example.yourswelnes.core.worker.LocationWatchdogWorker
import com.example.yourswelnes.core.worker.NotificationSyncWorker
import com.example.yourswelnes.core.worker.ScheduleSyncWorker
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber

@HiltAndroidApp
class YourswelnesApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var locationNotificationManager: LocationNotificationManager
    @Inject lateinit var appNotificationManager: AppNotificationManager
    @Inject lateinit var authPrefs: AuthPreferencesDataStore
    @Inject lateinit var fcmPrefs: FcmPreferencesDataStore

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        locationNotificationManager.createChannels()
        appNotificationManager.createChannel()

        val workManager = WorkManager.getInstance(this)
        LocationUploadWorker.schedule(workManager)
        // Drain any pending records immediately on process creation (covers: normal launch,
        // WorkManager process recreation, post-reboot first open). Runs as soon as network
        // is available so users never wait a full 15-min periodic tick after offline periods.
        LocationUploadWorker.scheduleOneTime(workManager)
        ScheduleSyncWorker.schedule(workManager)
        // Offline-capable watchdog: restarts LocationForegroundService every 15 min if it was
        // killed by OEM battery optimisation while the tracking window is active. No network
        // constraint — this is the only recovery path when internet is unavailable.
        LocationWatchdogWorker.schedule(workManager)
        // Cancel any previously scheduled notification polling worker — FCM handles delivery now.
        NotificationSyncWorker.cancel(workManager)
        AppInstallationSyncWorker.schedulePeriodic(workManager)
        AppInstallationSyncWorker.scheduleOneTime(workManager)

        // Proactively fetch and cache the FCM registration token so it is available
        // for the next login call, even if onNewToken has not fired in this process.
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result
                Timber.tag("App").i("FCM TOKEN GENERATED: $token")
                CoroutineScope(Dispatchers.IO).launch {
                    fcmPrefs.saveFcmToken(token)
                }
            } else {
                Timber.tag("App").w("FCM token fetch failed: ${task.exception?.message}")
            }
        }

        ensureLocationServiceRunning(workManager)
    }

    /**
     * Start LocationForegroundService whenever the process is created and a user session exists.
     * This covers: normal app launches, WorkManager process re-creation, and redundant START_STICKY
     * restarts. On Android 12+ a background process start (e.g. triggered by a WorkManager job)
     * throws ForegroundServiceStartNotAllowedException — we catch it, log it, and schedule an
     * immediate upload worker so pending records are not stranded when the service can't start.
     * START_STICKY handles service resurrection once the app returns to the foreground.
     */
    private fun ensureLocationServiceRunning(workManager: WorkManager) {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                if (authPrefs.isLoggedIn()) {
                    Timber.tag("App").i("WORKER STARTED | User is logged in — ensuring LocationForegroundService is running")
                    ContextCompat.startForegroundService(
                        this@YourswelnesApplication,
                        LocationForegroundService.startIntent(this@YourswelnesApplication)
                    )
                } else {
                    Timber.tag("App").d("No active session — skipping LocationForegroundService start")
                }
            } catch (e: Exception) {
                // ForegroundServiceStartNotAllowedException on Android 12+ when process is
                // created in background. Schedule an immediate upload so pending locations are
                // drained as soon as network is available; START_STICKY resurrects the service
                // when the user next brings the app to the foreground.
                Timber.tag("App").w("Cannot start LocationForegroundService from background context — ${e.message}")
                Timber.tag("App").w("Scheduling immediate upload worker as fallback; START_STICKY will restore collection")
                LocationUploadWorker.scheduleOneTime(workManager)
            }
        }
    }
}

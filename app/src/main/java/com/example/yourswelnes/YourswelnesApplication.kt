package com.example.yourswelnes

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.WorkManager
import com.example.yourswelnes.core.notification.LocationNotificationManager
import com.example.yourswelnes.core.worker.AppInstallationSyncWorker
import com.example.yourswelnes.core.worker.LocationUploadWorker
import com.example.yourswelnes.core.worker.NotificationSyncWorker
import com.example.yourswelnes.core.worker.ScheduleSyncWorker
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import timber.log.Timber

@HiltAndroidApp
class YourswelnesApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var locationNotificationManager: LocationNotificationManager

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
        val workManager = WorkManager.getInstance(this)
        LocationUploadWorker.schedule(workManager)
        ScheduleSyncWorker.schedule(workManager)
        NotificationSyncWorker.schedule(workManager)
        AppInstallationSyncWorker.schedulePeriodic(workManager)
        AppInstallationSyncWorker.scheduleOneTime(workManager)
    }
}

package com.example.yourswelnes.core.receiver

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.work.WorkManager
import com.example.yourswelnes.core.service.LocationForegroundService
import com.example.yourswelnes.core.worker.LocationUploadWorker
import com.example.yourswelnes.core.worker.NotificationSyncWorker
import com.example.yourswelnes.core.worker.ScheduleSyncWorker
import timber.log.Timber

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED ->
                Timber.tag("BootReceiver").d("ACTION_BOOT_COMPLETED received")
            Intent.ACTION_MY_PACKAGE_REPLACED ->
                Timber.tag("BootReceiver").d("ACTION_MY_PACKAGE_REPLACED received — re-arming tracking after app update")
            else -> return
        }

        val hasPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            Timber.tag("BootReceiver").w("Location permission not granted — skipping auto-start after boot")
            return
        }

        Timber.tag("BootReceiver").i(
            "DEVICE REBOOT RECOVERY | Permissions OK — " +
            "restarting LocationForegroundService and re-registering all workers"
        )
        ContextCompat.startForegroundService(context, LocationForegroundService.startIntent(context))
        val workManager = WorkManager.getInstance(context)
        LocationUploadWorker.schedule(workManager)
        // Drain any locations collected before the reboot as soon as network is available,
        // rather than waiting for the next 15-min periodic tick.
        LocationUploadWorker.scheduleOneTime(workManager)
        ScheduleSyncWorker.schedule(workManager)
        // FCM handles notification delivery — cancel any stale polling worker that may
        // have been re-enqueued by a previous app version.
        NotificationSyncWorker.cancel(workManager)
        Timber.tag("BootReceiver").d("DEVICE REBOOT RECOVERY | Service start and worker re-registration complete")
    }
}

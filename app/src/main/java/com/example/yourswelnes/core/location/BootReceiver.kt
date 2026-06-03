package com.example.yourswelnes.core.location

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.work.WorkManager
import com.example.yourswelnes.core.worker.LocationUploadWorker
import timber.log.Timber

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val hasPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            Timber.d("Boot completed but location permission not granted — skipping service start")
            return
        }

        Timber.d("Boot completed — starting LocationForegroundService and scheduling upload worker")
        ContextCompat.startForegroundService(context, LocationForegroundService.startIntent(context))
        LocationUploadWorker.schedule(WorkManager.getInstance(context))
    }
}

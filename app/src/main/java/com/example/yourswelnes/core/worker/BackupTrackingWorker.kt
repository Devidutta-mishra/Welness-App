package com.example.yourswelnes.core.worker

import android.content.Context
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.yourswelnes.core.datastore.AuthPreferencesDataStore
import com.example.yourswelnes.core.datastore.LocationPreferencesDataStore
import com.example.yourswelnes.core.location.AlarmHandoffWakeLock
import com.example.yourswelnes.core.location.LocationScheduler
import com.example.yourswelnes.core.location.LocationServiceState
import com.example.yourswelnes.core.service.LocationForegroundService
import com.example.yourswelnes.core.tracking.StandbyBucketMonitor
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.Date
import java.util.concurrent.TimeUnit
import timber.log.Timber

/**
 * One-shot JobScheduler fail-safe for the exact tracking-window alarm.
 *
 * The failure it covers: aggressive OEM skins (MIUI, ColorOS, FuntouchOS, OneUI) put the app in
 * a force-stopped-like state when the user swipes it away from Recents and WIPE its pending alarm
 * intents — including the [android.app.AlarmManager.setAlarmClock] registration that opens the
 * tracking window. Persisted JobScheduler jobs survive that same swipe (Google's vitals program
 * penalizes OEMs that kill scheduled jobs, so OEM battery managers leave them alone), which makes
 * a WorkManager request aimed at the same instant the most resilient backup trigger available.
 *
 * Strictly an external observer / safety net for the existing pipeline:
 *   • If [LocationForegroundService] is already running when this fires, the alarm path worked —
 *     return [Result.success] immediately and touch nothing.
 *   • If it is NOT running, the alarm was wiped overnight — wake the system (same
 *     [AlarmHandoffWakeLock] hand-off contract the alarm receiver uses) and start the service
 *     with the IDENTICAL start configuration ([LocationForegroundService.startIntent]).
 *
 * It never modifies pipeline state: no alarm re-arms (the watchdog's idempotent self-heal and the
 * app/receiver paths own that — re-arming from in here would REPLACE-cancel this very run), no
 * upload scheduling, no config writes. All failure modes return [Result.success] so the fail-safe
 * can never crash, retry-storm, or interfere with the layers it is backing up.
 *
 * Armed by TrackingAlarmScheduler right next to the alarm registration; cancelled on logout via
 * TrackingAlarmScheduler.cancel().
 */
@HiltWorker
class BackupTrackingWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val authPrefs: AuthPreferencesDataStore,
    private val locationPrefs: LocationPreferencesDataStore,
    private val locationScheduler: LocationScheduler,
    private val standbyBucketMonitor: StandbyBucketMonitor
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result =
        try {
            runBackupCheck()
        } catch (e: Exception) {
            // A safety net must be invisible when it fails: log and report success so WorkManager
            // never retry-storms a path the watchdog already re-covers every 15 minutes.
            Timber.tag(TAG).e(e, "BACKUP WORKER ERROR | swallowed — fail-safe never crashes the pipeline")
            Result.success()
        }

    private suspend fun runBackupCheck(): Result {
        Timber.tag(TAG).i("BACKUP WORKER FIRED | verifying the exact alarm opened the tracking window")

        // Diagnostic only: if the OS has demoted the app to the RESTRICTED standby bucket, both
        // the alarm and this job are quota-frozen — record it so the health monitor UI can tell
        // the user their phone is freezing the app.
        standbyBucketMonitor.checkAndRecord(source = "BackupTrackingWorker")

        // Gate 1: only act for an authenticated session.
        if (!authPrefs.isLoggedIn()) {
            Timber.tag(TAG).d("BACKUP SKIPPED | No active session")
            return Result.success()
        }

        // Gate 2: only act inside the tracking window. setInitialDelay is a MINIMUM — Doze or OEM
        // throttling can run this job late, and waking the service after the window closed would
        // be wrong (it would only start and immediately self-stop).
        val startTime = locationPrefs.getTrackingStartTime()
        val endTime = locationPrefs.getTrackingEndTime()
        if (startTime == null || endTime == null) {
            Timber.tag(TAG).w("BACKUP SKIPPED | No cached tracking window")
            return Result.success()
        }
        if (!locationScheduler.isInTrackingWindow(startTime, endTime)) {
            Timber.tag(TAG).w(
                "BACKUP SKIPPED | Fired outside window $startTime–$endTime " +
                "(job was deferred past the window, or the window moved) — nothing to wake"
            )
            return Result.success()
        }

        // Gate 3: the success path. Service already up means the alarm survived — the backup has
        // nothing to do and must do nothing.
        if (LocationServiceState.isRunning.value) {
            Timber.tag(TAG).i(
                "ALARM PATH HEALTHY | LocationForegroundService already running — backup idle"
            )
            return Result.success()
        }

        // Alarm was wiped (swipe-to-kill) — this is the exact scenario the backup exists for.
        // Same hand-off contract as TrackingAlarmReceiver: hold the CPU until the service has
        // acquired its own sustained collection lock (the service releases this lock; its 60 s
        // timeout is the leak-proof ceiling), then start with the identical configuration.
        Timber.tag(TAG).w(
            "BACKUP RESTART | Alarm intent was wiped (OEM swipe-kill / stopped state) — " +
            "starting LocationForegroundService from the WorkManager fail-safe"
        )
        AlarmHandoffWakeLock.acquire(applicationContext)
        try {
            ContextCompat.startForegroundService(
                applicationContext,
                LocationForegroundService.startIntent(applicationContext)
            )
            Timber.tag(TAG).i("BACKUP RESTART | startForegroundService dispatched — collection resuming")
        } catch (e: Exception) {
            // ForegroundServiceStartNotAllowedException (Android 12+ without the battery
            // exemption) or an OEM SecurityException. No service is coming — release the hand-off
            // lock now instead of pinning the CPU until its timeout. The 15-min watchdog and the
            // next app open remain the recovery layers, exactly as on the alarm path.
            AlarmHandoffWakeLock.release()
            Timber.tag(TAG).e(
                e,
                "BACKUP RESTART FAILED | FGS start blocked from background — " +
                "battery-optimization exemption required; watchdog/app-open will recover"
            )
        }
        return Result.success()
    }

    companion object {
        private const val TAG = "BackupTracking"
        const val WORK_NAME = "backup_tracking_window_start"

        /**
         * Enqueues the one-shot backup aimed at the same instant as the exact alarm.
         * Unique-name REPLACE mirrors the alarm's stable-PendingIntent semantics: every re-arm
         * (app start, watchdog self-heal, post-sync window change) retargets the single pending
         * backup instead of stacking duplicates, and a backend window change retargets it too.
         * No constraints and no expedite: expedited work forbids an initial delay, and the only
         * requirement is surviving until the target wall-clock instant.
         */
        fun schedule(workManager: WorkManager, delayMs: Long) {
            val safeDelayMs = delayMs.coerceAtLeast(0L)
            val request = OneTimeWorkRequestBuilder<BackupTrackingWorker>()
                .setInitialDelay(safeDelayMs, TimeUnit.MILLISECONDS)
                .addTag(WORK_NAME)
                .build()
            workManager.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
            Timber.tag(TAG).i(
                "BACKUP WORKER ARMED | fires in ${safeDelayMs / 1000}s " +
                "(~${Date(System.currentTimeMillis() + safeDelayMs)}) — " +
                "JobScheduler-persisted fail-safe for the window-start alarm"
            )
        }

        fun cancel(workManager: WorkManager) {
            workManager.cancelUniqueWork(WORK_NAME)
            Timber.tag(TAG).d("BACKUP WORKER CANCELLED | pending window-start backup removed")
        }
    }
}

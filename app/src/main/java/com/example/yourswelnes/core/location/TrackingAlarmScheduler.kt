package com.example.yourswelnes.core.location

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.yourswelnes.core.datastore.LocationPreferencesDataStore
import com.example.yourswelnes.core.receiver.TrackingAlarmReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

/**
 * Schedules the EXACT alarm that opens the daily tracking window.
 *
 * Why an exact alarm and not WorkManager / a coroutine delay: Deep Doze defers every WorkManager
 * job, every standard alarm, and suspends timers (so a `delay()` inside a sleeping service never
 * fires on time) until the device's next maintenance window — which during an overnight idle can
 * be hours apart. [AlarmManager.setExactAndAllowWhileIdle] is the only scheduling primitive that
 * fires at a precise wall-clock time WHILE the device is in Doze, so it is the only reliable way
 * to guarantee the foreground service is (re)started exactly at `locationTrackStartTime` after the
 * phone has been locked and offline all night. This is the fix for the "Overnight Deep Doze"
 * failure that the periodic [com.example.yourswelnes.core.worker.LocationWatchdogWorker] could not
 * solve (periodic WorkManager is itself Doze-deferred).
 *
 * Separation of concerns: this class only WAKES the pipeline. Collection runs in
 * [com.example.yourswelnes.core.service.LocationForegroundService] and upload runs in
 * [com.example.yourswelnes.core.worker.LocationUploadWorker] — both untouched here, so the auth and
 * upload paths are unaffected. It is fully offline-safe: every value it reads (start/end time)
 * comes from the locally cached config in DataStore; it never touches the network.
 */
@Singleton
class TrackingAlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val locationPrefs: LocationPreferencesDataStore,
    private val locationScheduler: LocationScheduler
) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /**
     * Arms an exact alarm for the next occurrence of the cached tracking-start time. Safe to call
     * repeatedly — the PendingIntent is stable (same request code + FLAG_UPDATE_CURRENT), so
     * re-scheduling REPLACES the previous alarm instead of stacking duplicates.
     *
     * Offline behaviour: reads start/end purely from DataStore. If no config has been cached yet
     * (user has never been online since install) it logs and returns without arming —
     * ScheduleSyncWorker will cache the config when network returns and
     * HomeViewModel.rescheduleWorkers() / Application.onCreate() will re-arm on the next launch.
     */
    suspend fun scheduleNextWindowStart() {
        val startTime = locationPrefs.getTrackingStartTime()
        val endTime = locationPrefs.getTrackingEndTime()
        if (startTime == null || endTime == null) {
            Timber.tag(TAG).w(
                "ALARM SKIPPED | No cached tracking window yet — exact alarm not armed. " +
                "Will arm once ScheduleSyncWorker caches the config."
            )
            return
        }

        val triggerAtMs = locationScheduler.nextWindowStartEpochMillis(startTime)
        if (triggerAtMs == null) {
            Timber.tag(TAG).w("ALARM SKIPPED | Unparseable start time '$startTime' — not arming")
            return
        }

        val pendingIntent = buildPendingIntent()
        try {
            if (canScheduleExact()) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, triggerAtMs, pendingIntent
                )
                Timber.tag(TAG).i(
                    "EXACT ALARM ARMED | window start=$startTime | fires at ${Date(triggerAtMs)} " +
                    "| setExactAndAllowWhileIdle (Doze-exempt)"
                )
            } else {
                // SCHEDULE_EXACT_ALARM revoked (Android 12+). Fall back to an inexact-but-
                // Doze-allowed alarm so collection still recovers within a maintenance window;
                // the onboarding flow routes the user to grant "Alarms & reminders" for precision.
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, triggerAtMs, pendingIntent
                )
                Timber.tag(TAG).w(
                    "INEXACT ALARM ARMED | Exact-alarm permission not granted — using " +
                    "setAndAllowWhileIdle fallback. Window may open a few minutes late. " +
                    "Grant 'Alarms & reminders' for precise starts."
                )
            }
        } catch (e: SecurityException) {
            // Some OEMs throw even when canScheduleExactAlarms() reports true. Degrade gracefully
            // rather than crash — an inexact Doze alarm is far better than no alarm at all.
            Timber.tag(TAG).e(e, "ALARM ERROR | Exact alarm denied by OS — falling back to inexact")
            runCatching {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, pendingIntent)
            }
        }
    }

    /** Cancels any pending tracking-window alarm. Called on logout so the leaving user's window
     *  never wakes the service for the next user. */
    fun cancel() {
        alarmManager.cancel(buildPendingIntent())
        Timber.tag(TAG).d("EXACT ALARM CANCELLED | Tracking-window alarm removed")
    }

    private fun canScheduleExact(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true // pre-Android 12 needs no runtime grant
        }

    private fun buildPendingIntent(): PendingIntent {
        val intent = Intent(context, TrackingAlarmReceiver::class.java).apply {
            action = TrackingAlarmReceiver.ACTION_WINDOW_START
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_WINDOW_START,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        private const val TAG = "TrackingAlarm"
        private const val REQUEST_CODE_WINDOW_START = 4201
    }
}

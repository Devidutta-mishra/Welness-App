package com.example.yourswelnes.core.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.example.yourswelnes.core.location.TrackingAlarmScheduler
import com.example.yourswelnes.core.service.LocationForegroundService
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Hilt entry point used by manifest-registered BroadcastReceivers (which cannot use constructor
 * injection) to reach the singleton [TrackingAlarmScheduler]. We avoid @AndroidEntryPoint here
 * because its BroadcastReceiver support requires a `super.onReceive()` call that the Kotlin
 * compiler rejects (the framework method is abstract until Hilt's post-compile bytecode transform).
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface TrackingAlarmEntryPoint {
    fun trackingAlarmScheduler(): TrackingAlarmScheduler
}

/**
 * Fires when the exact tracking-window alarm goes off (armed by [TrackingAlarmScheduler]).
 *
 * Doze-survival handoff sequence:
 *   1. Acquire a PARTIAL_WAKE_LOCK so the CPU cannot fall back to sleep before the foreground
 *      service has had a chance to call startForeground(). Without this the device can re-enter
 *      Doze in the gap between this broadcast returning and the service starting, and the start is
 *      silently dropped.
 *   2. startForegroundService() → [LocationForegroundService] promotes itself with
 *      FOREGROUND_SERVICE_TYPE_LOCATION, the only mode exempt from background-location limits, and
 *      begins polling FusedLocationProviderClient.
 *   3. Re-arm the alarm for tomorrow (exact alarms are one-shot) from the cached config — works
 *      entirely offline; no network is touched on this path.
 *   4. Release the wake lock.
 *
 * goAsync() keeps the receiver process alive across the brief suspend re-arm. The wake lock is
 * acquired with a hard timeout so an unexpected crash can never leak it.
 */
class TrackingAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_WINDOW_START) return
        Timber.tag(TAG).i("EXACT ALARM FIRED | Tracking window opening — waking collection pipeline")

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG).apply {
            setReferenceCounted(false)
            // Hard timeout safety net — we release explicitly below, but this guarantees the lock
            // is never leaked even if the handoff throws.
            acquire(WAKE_LOCK_TIMEOUT_MS)
        }
        Timber.tag(TAG).d("WAKELOCK ACQUIRED | CPU held for service handoff")

        // Start the existing collection service. It re-checks auth token, permission, club, and
        // window itself and stops if there's nothing to do, so we don't duplicate those gates here.
        try {
            ContextCompat.startForegroundService(
                context, LocationForegroundService.startIntent(context)
            )
            Timber.tag(TAG).i("SERVICE HANDOFF | startForegroundService dispatched from alarm")
        } catch (e: Exception) {
            // ForegroundServiceStartNotAllowedException (Android 12+) when battery optimisation is
            // still enabled. Onboarding enforces the exemption; log so it's diagnosable in the field.
            Timber.tag(TAG).e(
                e,
                "SERVICE HANDOFF FAILED | Could not start FGS from alarm — battery exemption required"
            )
        }

        // Re-arm tomorrow's alarm, then release the lock. goAsync() keeps the process alive for the
        // brief DataStore read that scheduleNextWindowStart() performs.
        val scheduler = EntryPointAccessors.fromApplication(
            context.applicationContext, TrackingAlarmEntryPoint::class.java
        ).trackingAlarmScheduler()
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                scheduler.scheduleNextWindowStart()
                Timber.tag(TAG).d("ALARM RE-ARMED | Next tracking window scheduled")
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Failed to re-arm next tracking alarm")
            } finally {
                if (wakeLock.isHeld) wakeLock.release()
                Timber.tag(TAG).d("WAKELOCK RELEASED | Handoff complete")
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "TrackingAlarm"
        private const val WAKE_LOCK_TAG = "yourswelnes:tracking_alarm_wakelock"
        private const val WAKE_LOCK_TIMEOUT_MS = 30_000L
        const val ACTION_WINDOW_START = "com.example.yourswelnes.ACTION_TRACKING_WINDOW_START"
    }
}

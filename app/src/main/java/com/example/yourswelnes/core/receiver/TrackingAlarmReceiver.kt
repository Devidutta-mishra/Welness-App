package com.example.yourswelnes.core.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.example.yourswelnes.core.location.AlarmHandoffWakeLock
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
 *   1. Acquire the shared [AlarmHandoffWakeLock] so the CPU cannot fall back to sleep before the
 *      foreground service has called startForeground() AND acquired its own sustained collection
 *      lock. Without this the device can re-enter Doze in the gap between this broadcast returning
 *      and the service start, and the start is silently dropped.
 *   2. startForegroundService() → [LocationForegroundService] promotes itself with
 *      FOREGROUND_SERVICE_TYPE_LOCATION, the only mode exempt from background-location limits, and
 *      begins polling FusedLocationProviderClient.
 *   3. Re-arm the alarm for tomorrow (exact alarms are one-shot) from the cached config — works
 *      entirely offline; no network is touched on this path.
 *   4. The SERVICE — not this receiver — releases the hand-off lock once the FIRST GPS coordinate
 *      has been persisted to Room, so the CPU is held through the full offline GPS cold start
 *      (10–45 s with no A-GPS). If the service never comes up (FGS blocked), we release it here in
 *      the catch, and the lock's 60 s hard timeout is the final net.
 *
 * goAsync() keeps the receiver process alive across the brief suspend re-arm; the hand-off lock
 * keeps the CPU awake across it too.
 */
class TrackingAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_WINDOW_START) return
        Timber.tag(TAG).i("EXACT ALARM FIRED | Tracking window opening — waking collection pipeline")

        // Hold the CPU awake BEFORE starting the service. The OS keeps the CPU up only for the
        // duration of onReceive(); the service releases this lock once its own collection lock has
        // taken over, so the CPU is never dropped during the alarm→service start. A hard timeout in
        // AlarmHandoffWakeLock guarantees it can never leak if the service never comes up.
        AlarmHandoffWakeLock.acquire(context)

        // Start the existing collection service. It re-checks auth token, permission, club, and
        // window itself and stops if there's nothing to do, so we don't duplicate those gates here.
        try {
            ContextCompat.startForegroundService(
                context, LocationForegroundService.startIntent(context)
            )
            Timber.tag(TAG).i("SERVICE HANDOFF | startForegroundService dispatched from alarm")
        } catch (e: Exception) {
            // ForegroundServiceStartNotAllowedException (Android 12+) when battery optimisation is
            // still enabled. No service is coming, so release the hand-off lock now rather than
            // pinning the CPU until the timeout. Onboarding enforces the exemption.
            Timber.tag(TAG).e(
                e,
                "SERVICE HANDOFF FAILED | Could not start FGS from alarm — releasing hand-off lock"
            )
            AlarmHandoffWakeLock.release()
        }

        // Re-arm tomorrow's alarm (exact alarms are one-shot). goAsync() keeps the process alive for
        // the brief DataStore read; the hand-off lock keeps the CPU awake across it. The lock is NOT
        // released here — the service owns its release once collection is genuinely under way.
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
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "TrackingAlarm"
        const val ACTION_WINDOW_START = "com.example.yourswelnes.ACTION_TRACKING_WINDOW_START"
    }
}

package com.example.yourswelnes.core.location

import android.content.Context
import android.os.PowerManager
import timber.log.Timber

/**
 * A single, process-wide PARTIAL_WAKE_LOCK that bridges the hand-off from [com.example.yourswelnes
 * .core.receiver.TrackingAlarmReceiver] to [com.example.yourswelnes.core.service
 * .LocationForegroundService].
 *
 * The gap it closes: when the exact alarm fires in Deep Doze, the system holds the CPU awake ONLY
 * for the duration of `BroadcastReceiver.onReceive()`. The instant `onReceive()` returns, the CPU
 * can suspend again — potentially BEFORE the foreground service has run `onCreate()` /
 * `startForeground()` and acquired its own sustained collection wake lock. In that window the
 * service start is silently dropped and nothing is collected until the screen is next turned on
 * (the exact symptom of "it only triggers at 6:30 when I unlock").
 *
 * Ownership contract:
 *   • [TrackingAlarmReceiver] calls [acquire] BEFORE `startForegroundService()`.
 *   • [LocationForegroundService] calls [release] only once the FIRST valid coordinate of the run
 *     has been successfully written to Room (or when it stops / leaves the window). Releasing any
 *     earlier is the "offline GPS trap": with no internet there is no A-GPS, the hardware chip
 *     needs a 10–45 s cold start, and a CPU allowed to sleep mid-warm-up means zero fixes all night.
 *   • A hard [TIMEOUT_MS] (60 s) is the strict ceiling, guaranteeing the lock can never leak even
 *     if the service never comes up or GPS never achieves a lock.
 *
 * It is intentionally a process-global singleton (not Hilt-injected): both a manifest-registered
 * BroadcastReceiver and the Service need to reach the SAME lock instance, and neither can rely on
 * the other's lifecycle.
 */
object AlarmHandoffWakeLock {
    private const val TAG = "TrackingAlarm"
    private const val WAKE_LOCK_TAG = "yourswelnes:alarm_service_handoff"

    // Generous ceiling: the service normally takes over within a second or two. This only exists so
    // a never-started service (FGS blocked, crash) cannot pin the CPU awake indefinitely.
    private const val TIMEOUT_MS = 60_000L

    private var wakeLock: PowerManager.WakeLock? = null

    /**
     * Acquire (or refresh the timeout on) the hand-off lock. Reference counting is disabled so a
     * rapid re-fire simply resets the timeout rather than stacking locks; [release] then drops it in
     * one call regardless of how many times this ran.
     */
    @Synchronized
    fun acquire(context: Context) {
        val pm = context.applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wl = wakeLock ?: pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG).also {
            it.setReferenceCounted(false)
            wakeLock = it
        }
        try {
            wl.acquire(TIMEOUT_MS)
            Timber.tag(TAG).d(
                "HANDOFF WAKELOCK ACQUIRED | CPU held across alarm→service start (timeout ${TIMEOUT_MS}ms)"
            )
        } catch (e: Exception) {
            // WAKE_LOCK permission missing / OEM restriction — the start is best-effort from here.
            Timber.tag(TAG).e(e, "Could not acquire hand-off wake lock — service start is best-effort")
        }
    }

    /** Release the hand-off lock if held. Idempotent and safe to call from any service start path. */
    @Synchronized
    fun release() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
                Timber.tag(TAG).d("HANDOFF WAKELOCK RELEASED | service has taken over the CPU")
            }
        }
    }
}

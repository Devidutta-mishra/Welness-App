package com.example.yourswelnes.core.location

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.yourswelnes.MainActivity
import com.example.yourswelnes.core.datastore.AuthPreferencesDataStore
import com.example.yourswelnes.core.datastore.LocationPreferencesDataStore
import com.example.yourswelnes.core.receiver.TrackingAlarmReceiver
import com.example.yourswelnes.core.service.LocationForegroundService
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

/**
 * Schedules the EXACT alarm that opens the daily tracking window — and decides, every time the
 * cached window may have changed, whether to arm that alarm or start collecting RIGHT NOW.
 *
 * Why an exact alarm and not WorkManager / a coroutine delay: Deep Doze defers every WorkManager
 * job, every standard alarm, and suspends timers (so a `delay()` inside a sleeping service never
 * fires on time) until the device's next maintenance window — which during an overnight idle can
 * be hours apart. An exact alarm is the only scheduling primitive that fires at a precise
 * wall-clock time WHILE the device is in Doze.
 *
 * Why [AlarmManager.setAlarmClock] specifically (not setExactAndAllowWhileIdle): both pull the
 * device out of Doze, but setExactAndAllowWhileIdle is still treated as an ordinary app alarm —
 * and aggressive OEM battery managers (Xiaomi/MIUI, Oppo/Realme ColorOS, Vivo FuntouchOS) silently
 * DEFER those for apps they have flagged "idle" overnight, which is exactly why the window only
 * opened once the screen was turned on. setAlarmClock registers a USER-VISIBLE alarm clock (the
 * same primitive the system Clock app uses); the OS guarantees it the strongest possible wake and
 * OEM skins do not defer it. It needs the same SCHEDULE_EXACT_ALARM grant, so when that is revoked
 * we fall back to an inexact Doze-allowed alarm.
 *
 * ── Immediate-evaluation (re-login / dynamic-update fix) ────────────────────────────────────────
 * [evaluateAndApply] is the entry point for app start, successful login, and post-sync. It reads
 * the LIVE cached window and chooses:
 *   • shouldStartNow  — now is inside the window: start the service immediately and bypass the
 *                       alarm (a window that is already open must not wait for a future trigger).
 *   • shouldSchedule  — now is before the window: arm the exact alarm for the upcoming start.
 * This closes the gap where a window moved to a time only minutes away (or already started) was
 * never picked up because the alarm had already been armed from the previous, stale timestamp.
 *
 * ── User-scoped isolation ───────────────────────────────────────────────────────────────────────
 * The PendingIntent request code is derived from the active user's id, so User A and User B
 * register independent alarms on the same device. Logout cancels exactly one user's alarm and can
 * never clobber another's.
 *
 * Separation of concerns: this class only WAKES / schedules the pipeline. Collection runs in
 * [LocationForegroundService] and upload in the workers — untouched here. It is offline-safe:
 * every value it reads comes from the locally cached config in DataStore; it never touches network.
 */
@Singleton
class TrackingAlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val locationPrefs: LocationPreferencesDataStore,
    private val authPrefs: AuthPreferencesDataStore,
    private val locationScheduler: LocationScheduler
) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /**
     * Immediate-evaluation entry. Call on app start, on successful login, and after every schedule
     * sync — anywhere the cached tracking window may have just changed.
     *
     * Scenario A (Future Window): `now < start` → arm the exact Doze-proof alarm for today's start.
     * Scenario B (Active Window): `start <= now < end` → BYPASS the alarm and start the foreground
     *   service immediately, then additionally arm the NEXT occurrence (tomorrow) so future days
     *   still open on their own.
     *
     * Reads the cached config only, so it is fully offline-safe; skips quietly if there is no
     * active session or no window cached yet.
     */
    suspend fun evaluateAndApply() {
        val userId = authPrefs.getUserId()
        if (userId == null) {
            Timber.tag(TAG).w("EVALUATE SKIPPED | No active user — nothing to schedule")
            return
        }
        val startTime = locationPrefs.getTrackingStartTime()
        val endTime = locationPrefs.getTrackingEndTime()
        if (startTime == null || endTime == null) {
            Timber.tag(TAG).w("EVALUATE SKIPPED | No cached tracking window yet")
            return
        }

        val shouldStartNow = locationScheduler.isInTrackingWindow(startTime, endTime)
        if (shouldStartNow) {
            // Scenario B — the window is already open. Start collecting at once; do not wait for a
            // future alarm. Still arm the next occurrence so tomorrow's window opens by itself.
            Timber.tag(TAG).i(
                "IMMEDIATE START | now inside $startTime–$endTime — starting service now (alarm bypassed)"
            )
            startServiceNow()
            armNextWindowStart(userId, startTime)
        } else {
            // Scenario A — the window has not opened yet. Arm the exact alarm for its start.
            Timber.tag(TAG).i("SCHEDULE | now before $startTime — arming exact alarm for next start")
            armNextWindowStart(userId, startTime)
        }
    }

    /**
     * Arms an exact alarm for the next occurrence of the cached start time WITHOUT an immediate
     * evaluation. Used by [TrackingAlarmReceiver] to re-arm tomorrow's window right after today's
     * alarm fires — the receiver has already started the service for the current window, so we must
     * NOT start it again. Safe to call repeatedly (the PendingIntent is stable per user, so a
     * re-arm REPLACES the previous alarm rather than stacking duplicates).
     */
    suspend fun scheduleNextWindowStart() {
        val userId = authPrefs.getUserId()
        if (userId == null) {
            Timber.tag(TAG).w("ALARM SKIPPED | No active user — exact alarm not armed")
            return
        }
        val startTime = locationPrefs.getTrackingStartTime()
        if (startTime == null || locationPrefs.getTrackingEndTime() == null) {
            Timber.tag(TAG).w(
                "ALARM SKIPPED | No cached tracking window yet — exact alarm not armed. " +
                "Will arm once ScheduleSyncWorker caches the config."
            )
            return
        }
        armNextWindowStart(userId, startTime)
    }

    /**
     * Cancels the LEAVING user's pending tracking-window alarm. Must be called on logout BEFORE the
     * auth session is cleared, because it targets the PendingIntent whose request code is derived
     * from the current user's id — once auth is wiped that id is gone. With user-scoped request
     * codes this cancels only this user's alarm and can never touch another user's registration.
     */
    suspend fun cancel() {
        val userId = authPrefs.getUserId()
        if (userId == null) {
            Timber.tag(TAG).w("ALARM CANCEL SKIPPED | No active user id (already cleared?)")
            return
        }
        alarmManager.cancel(buildPendingIntent(userId))
        Timber.tag(TAG).d("EXACT ALARM CANCELLED | user=$userId | tracking-window alarm removed")
    }

    // ── Internals ───────────────────────────────────────────────────────────────────────────────

    private fun armNextWindowStart(userId: String, startTime: String) {
        val triggerAtMs = locationScheduler.nextWindowStartEpochMillis(startTime)
        if (triggerAtMs == null) {
            Timber.tag(TAG).w("ALARM SKIPPED | Unparseable start time '$startTime' — not arming")
            return
        }

        val operation = buildPendingIntent(userId)

        if (!canScheduleExact()) {
            // SCHEDULE_EXACT_ALARM revoked (Android 12+). setAlarmClock needs the same grant, so fall
            // back to an inexact-but-Doze-allowed alarm; onboarding routes the user to grant
            // "Alarms & reminders" to restore precision.
            armInexact(userId, operation, triggerAtMs)
            return
        }

        try {
            // setAlarmClock(): registered as a USER-VISIBLE alarm clock, the strongest wake guarantee
            // the platform offers. It pulls the hardware out of Deep Doze AND — the reason we moved off
            // setExactAndAllowWhileIdle — OEM battery managers that defer ordinary app alarms overnight
            // do NOT defer a real alarm clock. The showIntent opens MainActivity if the user taps the
            // alarm chip the OS surfaces in the status bar / lock screen.
            val info = AlarmManager.AlarmClockInfo(triggerAtMs, buildShowIntent())
            alarmManager.setAlarmClock(info, operation)
            Timber.tag(TAG).i(
                "ALARM CLOCK ARMED | user=$userId | window start=$startTime | " +
                "fires at ${Date(triggerAtMs)} | setAlarmClock (user-visible, Doze + OEM-defer exempt)"
            )
        } catch (e: SecurityException) {
            // Some OEMs throw even when canScheduleExactAlarms() reports true. Degrade gracefully
            // rather than crash — an inexact Doze alarm is far better than no alarm at all.
            Timber.tag(TAG).e(e, "ALARM ERROR | setAlarmClock denied by OS — falling back to inexact")
            armInexact(userId, operation, triggerAtMs)
        }
    }

    /** Inexact, Doze-allowed fallback used when exact-alarm scheduling is unavailable or denied. */
    private fun armInexact(userId: String, operation: PendingIntent, triggerAtMs: Long) {
        runCatching {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, operation)
            Timber.tag(TAG).w(
                "INEXACT ALARM ARMED | user=$userId | Exact-alarm scheduling unavailable — using " +
                "setAndAllowWhileIdle fallback. Window may open a few minutes late."
            )
        }.onFailure { Timber.tag(TAG).e(it, "ALARM ERROR | Even the inexact fallback failed to arm") }
    }

    private fun startServiceNow() {
        try {
            ContextCompat.startForegroundService(context, LocationForegroundService.startIntent(context))
            Timber.tag(TAG).i("SERVICE START | startForegroundService dispatched (active window)")
        } catch (e: Exception) {
            // ForegroundServiceStartNotAllowedException when invoked from a background context
            // (e.g. ScheduleSyncWorker on Android 12+). The alarm we still arm, plus the periodic
            // LocationWatchdogWorker, recover collection; the service self-gates on the window when
            // it does start, so a slightly delayed start is safe.
            Timber.tag(TAG).w(e, "IMMEDIATE START FAILED | FGS blocked from background — alarm/watchdog will recover")
        }
    }

    private fun canScheduleExact(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true // pre-Android 12 needs no runtime grant
        }

    /**
     * Builds the broadcast PendingIntent whose request code is SCOPED TO THE USER, so two users on
     * the same device register independent alarms. The intent's action/component are identical
     * across users (the receiver is shared); the request code is what keeps the registrations
     * distinct, since PendingIntent identity includes the request code.
     */
    private fun buildPendingIntent(userId: String): PendingIntent {
        val intent = Intent(context, TrackingAlarmReceiver::class.java).apply {
            action = TrackingAlarmReceiver.ACTION_WINDOW_START
        }
        return PendingIntent.getBroadcast(
            context,
            requestCodeFor(userId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** Stable per-user request code: namespaced from other app PendingIntents and keyed by user id. */
    private fun requestCodeFor(userId: String): Int = REQUEST_CODE_WINDOW_START * 31 + userId.hashCode()

    /**
     * The "show" PendingIntent attached to [AlarmManager.AlarmClockInfo]. It opens when the user taps
     * the alarm-clock chip the system surfaces in the status bar / lock screen for an armed
     * setAlarmClock alarm. Routing it to [MainActivity] keeps that affordance meaningful — and
     * providing it is what makes the OS treat the alarm as genuinely user-visible (and therefore
     * Doze/OEM-defer exempt). It is shared across users; only the firing `operation` is user-scoped.
     */
    private fun buildShowIntent(): PendingIntent {
        val launch = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return PendingIntent.getActivity(
            context,
            REQUEST_CODE_SHOW_INTENT,
            launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        private const val TAG = "TrackingAlarm"
        private const val REQUEST_CODE_WINDOW_START = 4201
        private const val REQUEST_CODE_SHOW_INTENT = 4202
    }
}

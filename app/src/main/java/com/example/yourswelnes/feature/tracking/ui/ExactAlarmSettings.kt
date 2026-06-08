package com.example.yourswelnes.feature.tracking.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import timber.log.Timber

/**
 * Opens the "Alarms & reminders" (exact-alarm) permission screen through an ActivityResult
 * [launcher] so the caller's onResume / ActivityResult re-check always fires on return.
 *
 * Fallback order (stops at the first screen that opens):
 *   1. ACTION_REQUEST_SCHEDULE_EXACT_ALARM — focused per-app "Alarms & reminders" toggle (API 31+)
 *   2. ACTION_APPLICATION_DETAILS_SETTINGS — this app's details page (near-universal)
 *
 * Mirrors [launchBatterySettings]: every launch is guarded against ALL exceptions (not only
 * ActivityNotFoundException) because ActivityResultLauncher.launch() can throw SecurityException
 * or a vendor-specific RuntimeException on restricted OEM / enterprise ROMs; an uncaught one would
 * crash the app on the wizard step. If every option fails, the user gets a Toast instead of a crash.
 */
internal fun launchExactAlarmSettings(
    launcher: ActivityResultLauncher<Intent>,
    context: Context
) {
    val packageUri = Uri.parse("package:${context.packageName}")
    val candidates = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, packageUri))
        }
        add(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri))
    }

    for (intent in candidates) {
        try {
            launcher.launch(intent)
            return // a screen opened — the result callback re-checks canScheduleExactAlarms()
        } catch (e: ActivityNotFoundException) {
            Timber.tag("ExactAlarm").w("Exact-alarm intent unavailable: ${intent.action} — trying next")
        } catch (e: Exception) {
            Timber.tag("ExactAlarm").w(e, "Exact-alarm intent failed: ${intent.action} — trying next")
        }
    }

    Timber.tag("ExactAlarm").e("All exact-alarm settings intents failed — none could be opened")
    Toast.makeText(
        context,
        "Couldn't open the Alarms & reminders screen automatically. Please enable " +
            "\"Alarms & reminders\" for this app manually from your device Settings.",
        Toast.LENGTH_LONG
    ).show()
}

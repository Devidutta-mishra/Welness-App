package com.example.yourswelnes.feature.tracking.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import timber.log.Timber

/**
 * Opens the battery-optimization exemption screen through an ActivityResult [launcher] so the
 * caller's onResume / ActivityResult re-check always fires when the user returns.
 *
 * Fallback order (stops at the first screen that opens):
 *   1. ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS — focused per-app "Allow" dialog
 *   2. ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS  — full battery-optimization list
 *   3. ACTION_APPLICATION_DETAILS_SETTINGS          — this app's details page (near-universal)
 *
 * Every launch is guarded against ALL exceptions — not only ActivityNotFoundException. On
 * restricted OEM / enterprise ROMs, ActivityResultLauncher.launch() can throw SecurityException
 * or a vendor-specific RuntimeException; an uncaught one would crash the app on a mandatory
 * wizard step. If every option fails, the user gets a Toast with manual instructions instead of
 * a crash.
 *
 * Happy path is unchanged from the previous inline implementation: the first intent that
 * resolves is launched and the function returns immediately.
 */
internal fun launchBatterySettings(
    launcher: ActivityResultLauncher<Intent>,
    context: Context
) {
    val packageUri = Uri.parse("package:${context.packageName}")
    val candidates = listOf(
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri),
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri)
    )

    for (intent in candidates) {
        try {
            launcher.launch(intent)
            return // a screen opened — the result callback re-checks exemption status
        } catch (e: ActivityNotFoundException) {
            Timber.tag("BatteryOpt").w("Battery intent unavailable: ${intent.action} — trying next")
        } catch (e: Exception) {
            // SecurityException (restricted permission) or a vendor-specific RuntimeException.
            Timber.tag("BatteryOpt").w(e, "Battery intent failed: ${intent.action} — trying next")
        }
    }

    Timber.tag("BatteryOpt").e("All battery-settings intents failed — none could be opened")
    Toast.makeText(
        context,
        "Couldn't open battery settings automatically. Please disable battery optimization " +
            "for this app manually from your device Settings.",
        Toast.LENGTH_LONG
    ).show()
}

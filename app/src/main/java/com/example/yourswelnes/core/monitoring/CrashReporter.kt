package com.example.yourswelnes.core.monitoring

import android.os.Build
import com.google.firebase.crashlytics.FirebaseCrashlytics
import timber.log.Timber

/**
 * Utility for reporting crashes and non-fatal exceptions to Firebase Crashlytics.
 * This class ensures that monitoring does not interfere with the application's
 * primary functional flows.
 */
object CrashReporter {

    /**
     * Set user context for Crashlytics reports.
     * Call this after a successful login.
     */
    fun setUserContext(userId: String, clubId: Int?) {
        try {
            val crashlytics = FirebaseCrashlytics.getInstance()
            crashlytics.setUserId(userId)
            clubId?.let { crashlytics.setCustomKey("club_id", it.toString()) }
            crashlytics.setCustomKey("device_manufacturer", Build.MANUFACTURER)
            crashlytics.setCustomKey("device_model", Build.MODEL)
            crashlytics.setCustomKey("android_version", Build.VERSION.RELEASE)
        } catch (e: Exception) {
            Timber.tag("CrashReporter").e(e, "Failed to set user context")
        }
    }

    /**
     * Update only the club ID in Crashlytics.
     */
    fun setClubId(clubId: Int) {
        try {
            FirebaseCrashlytics.getInstance().setCustomKey("club_id", clubId.toString())
        } catch (e: Exception) {
            Timber.tag("CrashReporter").e(e, "Failed to set club ID")
        }
    }

    /**
     * Must be called once from Application.onCreate() before any other method.
     * Disables collection in debug builds to keep the production dashboard clean.
     */
    fun initialize(isDebug: Boolean) {
        try {
            FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(!isDebug)
        } catch (e: Exception) {
            Timber.tag("CrashReporter").e(e, "Failed to configure Crashlytics collection")
        }
    }

    /**
     * Clear user context on logout.
     */
    fun clearUserContext() {
        try {
            val crashlytics = FirebaseCrashlytics.getInstance()
            crashlytics.setUserId("")
            crashlytics.setCustomKey("club_id", "")
        } catch (e: Exception) {
            Timber.tag("CrashReporter").e(e, "Failed to clear user context")
        }
    }

    /**
     * Reports a non-fatal exception to Crashlytics.
     * Use this for significant failures that are caught and handled (e.g., API failures, Worker errors).
     */
    fun logNonFatal(exception: Throwable, message: String? = null) {
        try {
            message?.let { FirebaseCrashlytics.getInstance().log(it) }
            FirebaseCrashlytics.getInstance().recordException(exception)
            Timber.tag("CrashReporter").e(exception, "Non-fatal reported: $message")
        } catch (e: Exception) {
            // Never let monitoring crash the app
            Timber.tag("CrashReporter").e(e, "Failed to log non-fatal exception")
        }
    }

    /**
     * Logs a message to Crashlytics without an exception.
     * Useful for tracking the state leading up to a crash.
     */
    fun log(message: String) {
        try {
            FirebaseCrashlytics.getInstance().log(message)
            Timber.tag("CrashReporter").d("Crashlytics log: $message")
        } catch (e: Exception) {
            Timber.tag("CrashReporter").e(e, "Failed to log message")
        }
    }

    /**
     * Triggers a test crash for verification purposes.
     * ONLY USE IN DEBUG BUILDS.
     */
    fun triggerTestCrash() {
        if (com.example.yourswelnes.BuildConfig.DEBUG) {
            throw RuntimeException("Test Crash - Firebase Crashlytics Integration")
        } else {
            Timber.tag("CrashReporter").w("Attempted to trigger test crash in non-debug build")
        }
    }
}

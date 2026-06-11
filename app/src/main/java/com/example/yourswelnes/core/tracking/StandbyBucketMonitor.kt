package com.example.yourswelnes.core.tracking

import android.annotation.SuppressLint
import android.app.usage.UsageStatsManager
import android.content.Context
import com.example.yourswelnes.core.datastore.LocationPreferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

/**
 * Read-only probe for the App Standby bucket the OS has placed this package in.
 *
 * Why it matters: when a device demotes the app to [UsageStatsManager.STANDBY_BUCKET_RESTRICTED]
 * (heavy OEM battery managers do this to apps the user has not opened recently), the kernel-level
 * quota system freezes BOTH delivery mechanisms the tracking pipeline relies on — alarms are
 * deferred to at most one per day and jobs to one batch per day. At that point neither the exact
 * alarm nor the WorkManager backup can fire on time, and no amount of in-app retrying helps; only
 * the user changing the app's battery setting does. This probe makes that otherwise-invisible
 * state diagnosable: it is sampled on every scheduling pass and persisted so the Home tracking
 * health monitor can show the user that their phone is freezing the app.
 *
 * Querying the CALLING app's own bucket needs no permission. The check is observation-only and
 * fully exception-guarded — it can never block or break the scheduling paths that invoke it.
 * State is persisted to DataStore (never Room) so no database schema is touched.
 */
@Singleton
class StandbyBucketMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val locationPrefs: LocationPreferencesDataStore
) {

    /**
     * Samples the current standby bucket, logs it, and persists it for the health monitor UI.
     * Returns the raw bucket value, or null if the platform refused the query. Safe to call from
     * any scheduling path — all failure modes are swallowed after logging.
     *
     * Note on the RESTRICTED constant: it was added in API 30, but it is a compile-time-inlined
     * `static final int`, so referencing it is safe on API 29 (minSdk) — an API 29 device simply
     * never returns it. The comparison is `>=` rather than `==` so the even-more-frozen hidden
     * buckets above RESTRICTED (e.g. NEVER) are also reported as restricted.
     */
    @SuppressLint("InlinedApi") // STANDBY_BUCKET_RESTRICTED inlines to 45; API 29 never returns it
    suspend fun checkAndRecord(source: String): Int? {
        val bucket = try {
            val usageStats =
                context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            if (usageStats == null) {
                Timber.tag(TAG).w("BUCKET CHECK SKIPPED | UsageStatsManager unavailable | source=$source")
                return null
            }
            usageStats.appStandbyBucket
        } catch (e: Exception) {
            // Some OEM builds throw from usage-stats queries. Diagnostics must never take down
            // the scheduling path that asked for them.
            Timber.tag(TAG).w(e, "BUCKET CHECK FAILED | source=$source")
            return null
        }

        val restricted = bucket >= UsageStatsManager.STANDBY_BUCKET_RESTRICTED

        if (restricted) {
            Timber.tag(TAG).e(
                "STANDBY BUCKET RESTRICTED | bucket=${bucketName(bucket)}($bucket) | source=$source | " +
                "OS has frozen this app's alarms AND jobs (≈1 wake/day) — neither the exact alarm " +
                "nor the WorkManager backup can fire on time. Health monitor will flag this to the user."
            )
        } else {
            Timber.tag(TAG).d(
                "STANDBY BUCKET OK | bucket=${bucketName(bucket)}($bucket) | source=$source"
            )
        }

        runCatching { locationPrefs.saveStandbyBucketDiagnostic(bucket, restricted) }
            .onFailure { Timber.tag(TAG).w(it, "BUCKET PERSIST FAILED | diagnostic not saved") }

        return bucket
    }

    private fun bucketName(bucket: Int): String = when (bucket) {
        UsageStatsManager.STANDBY_BUCKET_ACTIVE -> "ACTIVE"
        UsageStatsManager.STANDBY_BUCKET_WORKING_SET -> "WORKING_SET"
        UsageStatsManager.STANDBY_BUCKET_FREQUENT -> "FREQUENT"
        UsageStatsManager.STANDBY_BUCKET_RARE -> "RARE"
        UsageStatsManager.STANDBY_BUCKET_RESTRICTED -> "RESTRICTED"
        else -> "UNKNOWN" // hidden buckets (EXEMPTED=5, NEVER=50) land here
    }

    private companion object {
        const val TAG = "StandbyBucket"
    }
}

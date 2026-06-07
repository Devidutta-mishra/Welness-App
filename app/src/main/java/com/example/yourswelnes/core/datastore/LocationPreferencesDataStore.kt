package com.example.yourswelnes.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map

private val Context.locationDataStore by preferencesDataStore(name = "location_prefs")

@Singleton
class LocationPreferencesDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.locationDataStore

    // --- Tracking config ---

    suspend fun saveTrackingConfig(
        startTime: String,
        endTime: String,
        intervalSeconds: Int,
        uploadIntervalMinutes: Int
    ) {
        dataStore.edit { prefs ->
            prefs[KEY_START_TIME] = startTime
            prefs[KEY_END_TIME] = endTime
            prefs[KEY_INTERVAL_SECONDS] = intervalSeconds
            prefs[KEY_UPLOAD_INTERVAL_MINUTES] = uploadIntervalMinutes
        }
    }

    suspend fun getTrackingStartTime(): String? = dataStore.data.firstOrNull()?.get(KEY_START_TIME)
    suspend fun getTrackingEndTime(): String? = dataStore.data.firstOrNull()?.get(KEY_END_TIME)
    suspend fun getTrackingIntervalSeconds(): Int = dataStore.data.firstOrNull()?.get(KEY_INTERVAL_SECONDS) ?: 30

    // Honors the backend-provided upload cadence saved by saveTrackingConfig. Falls back to the
    // default when unset and is coerced to >= 1 min so a 0/negative config can't spin the loop.
    suspend fun getUploadIntervalMinutes(): Int =
        (dataStore.data.firstOrNull()?.get(KEY_UPLOAD_INTERVAL_MINUTES) ?: DEFAULT_UPLOAD_INTERVAL_MINUTES)
            .coerceAtLeast(1)

    // --- Club info ---

    suspend fun saveClubInfo(clubId: Int, latitude: Double, longitude: Double, clubName: String) {
        dataStore.edit { prefs ->
            prefs[KEY_CLUB_ID] = clubId
            prefs[KEY_CLUB_LATITUDE] = latitude
            prefs[KEY_CLUB_LONGITUDE] = longitude
            prefs[KEY_CLUB_NAME] = clubName
        }
    }

    /** Removes all club info so location collection stops until a valid club is fetched. */
    suspend fun clearClubInfo() {
        dataStore.edit { prefs ->
            prefs.remove(KEY_CLUB_ID)
            prefs.remove(KEY_CLUB_LATITUDE)
            prefs.remove(KEY_CLUB_LONGITUDE)
            prefs.remove(KEY_CLUB_NAME)
        }
    }

    suspend fun getClubId(): Int? = dataStore.data.firstOrNull()?.get(KEY_CLUB_ID)
    suspend fun getClubLatitude(): Double? = dataStore.data.firstOrNull()?.get(KEY_CLUB_LATITUDE)
    suspend fun getClubLongitude(): Double? = dataStore.data.firstOrNull()?.get(KEY_CLUB_LONGITUDE)
    suspend fun getClubName(): String? = dataStore.data.firstOrNull()?.get(KEY_CLUB_NAME)

    // --- Upload sync status ---

    suspend fun saveLastSyncTime(timestamp: Long) {
        dataStore.edit { prefs -> prefs[KEY_LAST_SYNC_TIME] = timestamp }
    }

    suspend fun getLastSyncTime(): Long? = dataStore.data.firstOrNull()?.get(KEY_LAST_SYNC_TIME)

    val lastSyncTime = dataStore.data.map { it[KEY_LAST_SYNC_TIME] }

    // --- Schedule sync status ---

    suspend fun saveLastScheduleSyncTime(timestamp: Long) {
        dataStore.edit { prefs -> prefs[KEY_LAST_SCHEDULE_SYNC_TIME] = timestamp }
    }

    suspend fun getLastScheduleSyncTime(): Long? =
        dataStore.data.firstOrNull()?.get(KEY_LAST_SCHEDULE_SYNC_TIME)

    // --- Tracking health timestamps ---

    /** Updated by LocationForegroundService every time a GPS point is saved locally. */
    suspend fun saveLastLocationCollectionTime(timestamp: Long) {
        dataStore.edit { prefs -> prefs[KEY_LAST_LOCATION_COLLECTION_TIME] = timestamp }
    }

    suspend fun getLastLocationCollectionTime(): Long? =
        dataStore.data.firstOrNull()?.get(KEY_LAST_LOCATION_COLLECTION_TIME)

    /** Updated by LocationWatchdogWorker on every execution. */
    suspend fun saveLastWorkerExecutionTime(timestamp: Long) {
        dataStore.edit { prefs -> prefs[KEY_LAST_WORKER_EXECUTION_TIME] = timestamp }
    }

    suspend fun getLastWorkerExecutionTime(): Long? =
        dataStore.data.firstOrNull()?.get(KEY_LAST_WORKER_EXECUTION_TIME)

    val lastLocationCollectionTime = dataStore.data.map { it[KEY_LAST_LOCATION_COLLECTION_TIME] }
    val lastWorkerExecutionTime    = dataStore.data.map { it[KEY_LAST_WORKER_EXECUTION_TIME] }

    private companion object {
        const val DEFAULT_UPLOAD_INTERVAL_MINUTES = 2

        val KEY_START_TIME = stringPreferencesKey("tracking_start_time")
        val KEY_END_TIME = stringPreferencesKey("tracking_end_time")
        val KEY_INTERVAL_SECONDS = intPreferencesKey("tracking_interval_seconds")
        val KEY_UPLOAD_INTERVAL_MINUTES = intPreferencesKey("upload_interval_minutes")
        val KEY_CLUB_ID = intPreferencesKey("club_id")
        val KEY_CLUB_LATITUDE = doublePreferencesKey("club_latitude")
        val KEY_CLUB_LONGITUDE = doublePreferencesKey("club_longitude")
        val KEY_CLUB_NAME = stringPreferencesKey("club_name")
        val KEY_LAST_SYNC_TIME = longPreferencesKey("last_sync_time")
        val KEY_LAST_SCHEDULE_SYNC_TIME = longPreferencesKey("last_schedule_sync_time")
        val KEY_LAST_LOCATION_COLLECTION_TIME = longPreferencesKey("last_location_collection_time")
        val KEY_LAST_WORKER_EXECUTION_TIME = longPreferencesKey("last_worker_execution_time")
    }
}

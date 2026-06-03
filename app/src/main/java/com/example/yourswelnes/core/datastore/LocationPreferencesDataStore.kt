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
    suspend fun getUploadIntervalMinutes(): Int = dataStore.data.firstOrNull()?.get(KEY_UPLOAD_INTERVAL_MINUTES) ?: 10

    // --- Club info ---

    suspend fun saveClubInfo(clubId: Int, latitude: Double, longitude: Double) {
        dataStore.edit { prefs ->
            prefs[KEY_CLUB_ID] = clubId
            prefs[KEY_CLUB_LATITUDE] = latitude
            prefs[KEY_CLUB_LONGITUDE] = longitude
        }
    }

    suspend fun getClubId(): Int? = dataStore.data.firstOrNull()?.get(KEY_CLUB_ID)
    suspend fun getClubLatitude(): Double? = dataStore.data.firstOrNull()?.get(KEY_CLUB_LATITUDE)
    suspend fun getClubLongitude(): Double? = dataStore.data.firstOrNull()?.get(KEY_CLUB_LONGITUDE)

    // --- Sync status ---

    suspend fun saveLastSyncTime(timestamp: Long) {
        dataStore.edit { prefs -> prefs[KEY_LAST_SYNC_TIME] = timestamp }
    }

    suspend fun getLastSyncTime(): Long? = dataStore.data.firstOrNull()?.get(KEY_LAST_SYNC_TIME)

    val lastSyncTime = dataStore.data.map { it[KEY_LAST_SYNC_TIME] }

    private companion object {
        val KEY_START_TIME = stringPreferencesKey("tracking_start_time")
        val KEY_END_TIME = stringPreferencesKey("tracking_end_time")
        val KEY_INTERVAL_SECONDS = intPreferencesKey("tracking_interval_seconds")
        val KEY_UPLOAD_INTERVAL_MINUTES = intPreferencesKey("upload_interval_minutes")
        val KEY_CLUB_ID = intPreferencesKey("club_id")
        val KEY_CLUB_LATITUDE = doublePreferencesKey("club_latitude")
        val KEY_CLUB_LONGITUDE = doublePreferencesKey("club_longitude")
        val KEY_LAST_SYNC_TIME = longPreferencesKey("last_sync_time")
    }
}

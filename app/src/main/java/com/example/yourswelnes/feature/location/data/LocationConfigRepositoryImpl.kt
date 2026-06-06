package com.example.yourswelnes.feature.location.data

import com.example.yourswelnes.core.datastore.LocationPreferencesDataStore
import com.example.yourswelnes.feature.location.data.api.LocationApi
import com.example.yourswelnes.feature.location.data.mapper.toDomain
import com.example.yourswelnes.feature.location.model.LocationConfig
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

private const val TAG = "ScheduleSync"

@Singleton
class LocationConfigRepositoryImpl @Inject constructor(
    private val locationApi: LocationApi,
    private val locationPrefs: LocationPreferencesDataStore
) : LocationConfigRepository {

    /**
     * Fetches the latest tracking schedule from the server and caches it locally.
     *
     * On success — updates DataStore and returns the new config.
     * On failure — logs the cached fallback and returns failure so callers can react if needed.
     *   The cached schedule remains intact; tracking continues uninterrupted.
     */
    override suspend fun getLocationConfig(): Result<LocationConfig> {
        val result = runCatching {
            val config = locationApi.getLocationConfig().toDomain()
            locationPrefs.saveTrackingConfig(
                startTime = config.trackingStartTime,
                endTime = config.trackingEndTime,
                intervalSeconds = config.trackingIntervalSeconds,
                uploadIntervalMinutes = config.uploadIntervalMinutes
            )
            locationPrefs.saveLastScheduleSyncTime(System.currentTimeMillis())
            Timber.tag(TAG).i(
                "TRACKING WINDOW UPDATED | API SUCCESS | " +
                "start=${config.trackingStartTime} end=${config.trackingEndTime} " +
                "interval=${config.trackingIntervalSeconds}s saved locally"
            )
            config
        }

        result.onFailure { error ->
            val cachedStart = locationPrefs.getTrackingStartTime() ?: "N/A"
            val cachedEnd = locationPrefs.getTrackingEndTime() ?: "N/A"
            Timber.tag(TAG).w(
                "NO INTERNET | Schedule API failed — " +
                "TRACKING WINDOW LOADED from cache: start=$cachedStart end=$cachedEnd | reason=${error.message}"
            )
        }

        return result
    }

    /** Returns the last successfully fetched config from DataStore (used when offline). */
    override suspend fun getCachedLocationConfig(): LocationConfig? {
        val start = locationPrefs.getTrackingStartTime() ?: return null
        val end = locationPrefs.getTrackingEndTime() ?: return null
        return LocationConfig(
            trackingStartTime = start,
            trackingEndTime = end,
            trackingIntervalSeconds = locationPrefs.getTrackingIntervalSeconds(),
            uploadIntervalMinutes = locationPrefs.getUploadIntervalMinutes()
        )
    }
}

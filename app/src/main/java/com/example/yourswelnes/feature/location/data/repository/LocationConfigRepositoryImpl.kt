package com.example.yourswelnes.feature.location.data.repository

import com.example.yourswelnes.core.datastore.LocationPreferencesDataStore
import com.example.yourswelnes.feature.location.data.remote.api.LocationApi
import com.example.yourswelnes.feature.location.data.remote.mapper.toDomain
import com.example.yourswelnes.feature.location.domain.model.LocationConfig
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

@Singleton
class LocationConfigRepositoryImpl @Inject constructor(
    private val locationApi: LocationApi,
    private val locationPrefs: LocationPreferencesDataStore
) : LocationConfigRepository {

    override suspend fun getLocationConfig(): Result<LocationConfig> = runCatching {
        val config = locationApi.getLocationConfig().toDomain()
        locationPrefs.saveTrackingConfig(
            startTime = config.trackingStartTime,
            endTime = config.trackingEndTime,
            intervalSeconds = config.trackingIntervalSeconds,
            uploadIntervalMinutes = config.uploadIntervalMinutes
        )
        config
    }.onFailure {
        Timber.e(it, "Failed to fetch location config")
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

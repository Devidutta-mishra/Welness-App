package com.example.yourswelnes.feature.location.data.repository

import com.example.yourswelnes.data.local.room.dao.LocationDao
import com.example.yourswelnes.data.local.room.entity.LocationEntity
import com.example.yourswelnes.feature.location.domain.model.LocationRecord
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

@Singleton
class LocationRepositoryImpl @Inject constructor(
    private val locationDao: LocationDao
) : LocationRepository {

    override suspend fun saveLocation(record: LocationRecord) {
        runCatching { locationDao.insert(record.toEntity()) }
            .onFailure { Timber.e(it, "Failed to save location record") }
    }

    override suspend fun getPendingLocations(): List<LocationRecord> =
        runCatching { locationDao.getPendingLocations().map { it.toDomain() } }
            .onFailure { Timber.e(it, "Failed to query pending locations") }
            .getOrDefault(emptyList())

    override suspend fun markAsUploaded(ids: List<Long>) {
        if (ids.isEmpty()) return
        runCatching { locationDao.markAsUploaded(ids) }
            .onFailure { Timber.e(it, "Failed to mark locations as uploaded") }
    }

    private fun LocationRecord.toEntity() = LocationEntity(
        id = id,
        latitude = latitude,
        longitude = longitude,
        distance = distance,
        timestamp = timestamp,
        uploaded = uploaded,
        createdAt = createdAt
    )

    private fun LocationEntity.toDomain() = LocationRecord(
        id = id,
        latitude = latitude,
        longitude = longitude,
        distance = distance,
        timestamp = timestamp,
        uploaded = uploaded,
        createdAt = createdAt
    )
}

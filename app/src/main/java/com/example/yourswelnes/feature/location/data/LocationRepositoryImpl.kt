package com.example.yourswelnes.feature.location.data

import com.example.yourswelnes.core.database.dao.LocationDao
import com.example.yourswelnes.core.database.entity.LocationEntity
import com.example.yourswelnes.feature.location.model.LocationRecord
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

// Stay well under SQLite's 999 host-parameter ceiling for IN (:ids) on API 29 devices.
private const val MARK_CHUNK_SIZE = 500

@Singleton
class LocationRepositoryImpl @Inject constructor(
    private val locationDao: LocationDao
) : LocationRepository {

    override suspend fun saveLocation(record: LocationRecord): Boolean =
        runCatching { locationDao.insert(record.toEntity()) }
            .onFailure { Timber.e(it, "Failed to save location record") }
            .isSuccess

    override suspend fun getPendingLocations(userId: String, limit: Int): List<LocationRecord> =
        runCatching { locationDao.getPendingLocations(userId, limit).map { it.toDomain() } }
            .onFailure { Timber.e(it, "Failed to query pending locations") }
            .getOrDefault(emptyList())

    override suspend fun markAsUploaded(ids: List<Long>) {
        if (ids.isEmpty()) return
        runCatching {
            // Chunk so the IN (:ids) clause never exceeds SQLite's host-parameter limit
            // (999 on API 29's bundled SQLite). Batched uploads already keep lists small;
            // this is defence-in-depth so a large list can never silently fail to mark.
            ids.chunked(MARK_CHUNK_SIZE).forEach { locationDao.markAsUploaded(it) }
        }.onFailure { Timber.e(it, "Failed to mark locations as uploaded") }
    }

    override suspend fun purgeUploadedLocations() {
        runCatching {
            val deleted = locationDao.deleteUploaded()
            if (deleted > 0) Timber.d("Purged %d already-uploaded location rows", deleted)
        }.onFailure { Timber.e(it, "Failed to purge uploaded locations") }
    }

    private fun LocationRecord.toEntity() = LocationEntity(
        id = id,
        userId = userId,
        latitude = latitude,
        longitude = longitude,
        distance = distance,
        timestamp = timestamp,
        uploaded = uploaded,
        createdAt = createdAt
    )

    private fun LocationEntity.toDomain() = LocationRecord(
        id = id,
        userId = userId,
        latitude = latitude,
        longitude = longitude,
        distance = distance,
        timestamp = timestamp,
        uploaded = uploaded,
        createdAt = createdAt
    )
}

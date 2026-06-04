package com.example.yourswelnes.feature.location.data.repository

import com.example.yourswelnes.feature.location.domain.model.LocationRecord

interface LocationRepository {
    suspend fun saveLocation(record: LocationRecord)
    suspend fun getPendingLocations(userId: String): List<LocationRecord>
    suspend fun markAsUploaded(ids: List<Long>)
}

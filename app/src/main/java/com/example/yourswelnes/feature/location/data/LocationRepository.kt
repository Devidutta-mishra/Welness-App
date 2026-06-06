package com.example.yourswelnes.feature.location.data

import com.example.yourswelnes.feature.location.model.LocationRecord

interface LocationRepository {
    suspend fun saveLocation(record: LocationRecord)

    /** Returns up to [limit] oldest not-yet-uploaded records for the user. */
    suspend fun getPendingLocations(userId: String, limit: Int): List<LocationRecord>

    suspend fun markAsUploaded(ids: List<Long>)

    /** Deletes records the backend has already confirmed, keeping the table bounded. */
    suspend fun purgeUploadedLocations()
}

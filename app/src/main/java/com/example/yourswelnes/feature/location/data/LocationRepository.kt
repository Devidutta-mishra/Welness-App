package com.example.yourswelnes.feature.location.data

import com.example.yourswelnes.feature.location.model.LocationRecord

interface LocationRepository {
    /**
     * Persists one collected point. Returns true only when the row was actually written — the
     * collection service keys the alarm→service hand-off wake-lock release on this, so a failed
     * write must never be reported as success (the CPU would sleep before a single point exists).
     */
    suspend fun saveLocation(record: LocationRecord): Boolean

    /** Returns up to [limit] oldest not-yet-uploaded records for the user. */
    suspend fun getPendingLocations(userId: String, limit: Int): List<LocationRecord>

    suspend fun markAsUploaded(ids: List<Long>)

    /** Deletes records the backend has already confirmed, keeping the table bounded. */
    suspend fun purgeUploadedLocations()
}

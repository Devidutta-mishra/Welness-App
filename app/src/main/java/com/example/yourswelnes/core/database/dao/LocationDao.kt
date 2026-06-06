package com.example.yourswelnes.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.yourswelnes.core.database.entity.LocationEntity

@Dao
interface LocationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: LocationEntity)

    // LIMIT bounds how many rows are pulled into memory per upload batch, so a large offline
    // backlog can be drained in chunks instead of one unbounded fetch.
    @Query("SELECT * FROM locations WHERE uploaded = 0 AND user_id = :userId ORDER BY created_at ASC LIMIT :limit")
    suspend fun getPendingLocations(userId: String, limit: Int): List<LocationEntity>

    @Query("UPDATE locations SET uploaded = 1 WHERE id IN (:ids)")
    suspend fun markAsUploaded(ids: List<Long>)

    // Reclaims space from rows the backend has already confirmed. Nothing reads uploaded rows,
    // so removing them keeps the table bounded over long-term use. Returns rows deleted.
    @Query("DELETE FROM locations WHERE uploaded = 1")
    suspend fun deleteUploaded(): Int
}

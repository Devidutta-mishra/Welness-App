package com.example.yourswelnes.data.local.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.yourswelnes.data.local.room.entity.LocationEntity

@Dao
interface LocationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: LocationEntity)

    @Query("SELECT * FROM locations WHERE uploaded = 0 ORDER BY created_at ASC")
    suspend fun getPendingLocations(): List<LocationEntity>

    @Query("UPDATE locations SET uploaded = 1 WHERE id IN (:ids)")
    suspend fun markAsUploaded(ids: List<Long>)
}

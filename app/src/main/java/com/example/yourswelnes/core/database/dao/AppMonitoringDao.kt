package com.example.yourswelnes.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.yourswelnes.core.database.entity.AppMonitoringEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AppMonitoringDao {

    @Query("SELECT * FROM app_monitoring ORDER BY appName ASC")
    fun getAll(): Flow<List<AppMonitoringEntity>>

    @Query("SELECT * FROM app_monitoring ORDER BY appName ASC")
    suspend fun getAllOnce(): List<AppMonitoringEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(apps: List<AppMonitoringEntity>)

    @Query("DELETE FROM app_monitoring")
    suspend fun deleteAll()

    @Transaction
    suspend fun replaceAll(apps: List<AppMonitoringEntity>) {
        deleteAll()
        insertAll(apps)
    }
}

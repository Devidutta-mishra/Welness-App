package com.example.yourswelnes.data.local.room.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// Index backs the hot upload query (WHERE uploaded = 0 AND user_id = :userId ORDER BY created_at),
// so draining pending rows stays fast even as the table grows.
@Entity(
    tableName = "locations",
    indices = [Index(value = ["user_id", "uploaded", "created_at"])]
)
data class LocationEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id") val id: Long = 0,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "latitude") val latitude: Double,
    @ColumnInfo(name = "longitude") val longitude: Double,
    @ColumnInfo(name = "distance") val distance: Float,
    @ColumnInfo(name = "timestamp") val timestamp: Long,
    @ColumnInfo(name = "uploaded") val uploaded: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long
)

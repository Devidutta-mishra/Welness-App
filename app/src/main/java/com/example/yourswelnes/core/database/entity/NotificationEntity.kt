package com.example.yourswelnes.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notifications")
data class NotificationEntity(
    @PrimaryKey
    @ColumnInfo(name = "id") val id: Int,
    // Every row is owned by the user whose session fetched it. All DAO queries
    // that return rows must filter by this column so User A's notifications never
    // appear for User B — even if both accounts log in on the same device.
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "message") val message: String,
    @ColumnInfo(name = "type") val type: String,
    @ColumnInfo(name = "is_read") val isRead: Boolean = false,
    // Tracks whether we've already shown an Android system notification for this entry.
    // Never overwritten once set to true — prevents infinite re-show across syncs/restarts.
    @ColumnInfo(name = "is_displayed") val isDisplayed: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: String
)

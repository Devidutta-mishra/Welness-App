package com.example.yourswelnes.data.local.room.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notifications")
data class NotificationEntity(
    @PrimaryKey
    @ColumnInfo(name = "id") val id: Int,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "message") val message: String,
    @ColumnInfo(name = "type") val type: String,
    @ColumnInfo(name = "is_read") val isRead: Boolean = false,
    // Tracks whether we've already shown an Android system notification for this entry.
    // Never overwritten once set to true — prevents infinite re-show across syncs/restarts.
    @ColumnInfo(name = "is_displayed") val isDisplayed: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: String
)

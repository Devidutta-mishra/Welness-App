package com.example.yourswelnes.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_monitoring")
data class AppMonitoringEntity(
    @PrimaryKey val appId: Int,
    val appName: String,
    val downloadLink: String,
    val packageName: String?,
    val isInstalled: Boolean
)

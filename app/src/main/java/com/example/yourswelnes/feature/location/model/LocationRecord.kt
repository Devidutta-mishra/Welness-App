package com.example.yourswelnes.feature.location.model

data class LocationRecord(
    val id: Long = 0,
    val userId: String,
    val latitude: Double,
    val longitude: Double,
    val distance: Float,
    val timestamp: Long,
    val uploaded: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

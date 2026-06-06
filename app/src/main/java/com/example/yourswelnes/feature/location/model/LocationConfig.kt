package com.example.yourswelnes.feature.location.model

data class LocationConfig(
    val trackingStartTime: String,
    val trackingEndTime: String,
    val trackingIntervalSeconds: Int,
    val uploadIntervalMinutes: Int
)

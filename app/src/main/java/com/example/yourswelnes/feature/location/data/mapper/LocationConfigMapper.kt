package com.example.yourswelnes.feature.location.data.mapper

import com.example.yourswelnes.feature.location.data.dto.LocationConfigDto
import com.example.yourswelnes.feature.location.model.LocationConfig

fun LocationConfigDto.toDomain(): LocationConfig = LocationConfig(
    trackingStartTime = trackingStartTime ?: "06:00",
    trackingEndTime = trackingEndTime ?: "12:00",
    trackingIntervalSeconds = trackingIntervalSeconds ?: 30,
    uploadIntervalMinutes = uploadIntervalMinutes ?: 10
)

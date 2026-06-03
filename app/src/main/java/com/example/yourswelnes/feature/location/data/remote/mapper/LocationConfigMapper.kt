package com.example.yourswelnes.feature.location.data.remote.mapper

import com.example.yourswelnes.feature.location.data.remote.dto.LocationConfigDto
import com.example.yourswelnes.feature.location.domain.model.LocationConfig

fun LocationConfigDto.toDomain(): LocationConfig = LocationConfig(
    trackingStartTime = trackingStartTime ?: "06:00",
    trackingEndTime = trackingEndTime ?: "12:00",
    trackingIntervalSeconds = trackingIntervalSeconds ?: 30,
    uploadIntervalMinutes = uploadIntervalMinutes ?: 10
)

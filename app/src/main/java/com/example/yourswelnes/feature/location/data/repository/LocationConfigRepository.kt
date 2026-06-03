package com.example.yourswelnes.feature.location.data.repository

import com.example.yourswelnes.feature.location.domain.model.LocationConfig

interface LocationConfigRepository {
    suspend fun getLocationConfig(): Result<LocationConfig>
    suspend fun getCachedLocationConfig(): LocationConfig?
}

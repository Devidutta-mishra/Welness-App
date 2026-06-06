package com.example.yourswelnes.feature.location.data

import com.example.yourswelnes.feature.location.model.LocationConfig

interface LocationConfigRepository {
    suspend fun getLocationConfig(): Result<LocationConfig>
    suspend fun getCachedLocationConfig(): LocationConfig?
}

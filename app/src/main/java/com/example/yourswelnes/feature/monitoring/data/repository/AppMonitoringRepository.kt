package com.example.yourswelnes.feature.monitoring.data.repository

import com.example.yourswelnes.feature.monitoring.domain.model.AppStatus
import kotlinx.coroutines.flow.Flow

interface AppMonitoringRepository {
    fun getApps(): Flow<List<AppStatus>>
    suspend fun syncApps(): Result<Unit>
}

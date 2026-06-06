package com.example.yourswelnes.feature.monitoring.data

import com.example.yourswelnes.feature.monitoring.model.AppStatus
import kotlinx.coroutines.flow.Flow

interface AppMonitoringRepository {
    fun getApps(): Flow<List<AppStatus>>
    suspend fun syncApps(): Result<Unit>
}

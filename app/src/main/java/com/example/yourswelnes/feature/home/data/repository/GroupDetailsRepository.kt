package com.example.yourswelnes.feature.home.data.repository

import com.example.yourswelnes.feature.home.domain.model.ActivitySlot
import com.example.yourswelnes.feature.home.domain.model.Group

interface GroupDetailsRepository {
    suspend fun fetchGroupDetails(): Result<List<ActivitySlot>>
    suspend fun fetchGroups(forceRefresh: Boolean = false): Result<List<Group>>
    fun clearCache()
}

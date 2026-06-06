package com.example.yourswelnes.feature.home.data

import com.example.yourswelnes.feature.home.model.ActivitySlot
import com.example.yourswelnes.feature.home.model.Group

interface GroupDetailsRepository {
    suspend fun fetchGroupDetails(): Result<List<ActivitySlot>>
    suspend fun fetchGroups(forceRefresh: Boolean = false): Result<List<Group>>
    fun clearCache()
}

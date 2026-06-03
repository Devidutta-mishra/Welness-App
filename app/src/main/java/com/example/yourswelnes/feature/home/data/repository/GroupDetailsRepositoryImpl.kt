package com.example.yourswelnes.feature.home.data.repository

import com.example.yourswelnes.core.datastore.AuthPreferencesDataStore
import com.example.yourswelnes.feature.home.data.remote.api.GroupDetailsApi
import com.example.yourswelnes.feature.home.data.remote.dto.GroupDetailsRequest
import com.example.yourswelnes.feature.home.data.remote.mapper.toActivitySlots
import com.example.yourswelnes.feature.home.domain.model.ActivitySlot
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.firstOrNull
import timber.log.Timber

@Singleton
class GroupDetailsRepositoryImpl @Inject constructor(
    private val groupDetailsApi: GroupDetailsApi,
    private val authPreferences: AuthPreferencesDataStore
) : GroupDetailsRepository {

    override suspend fun fetchGroupDetails(): Result<List<ActivitySlot>> = runCatching {
        val user = authPreferences.cachedUser.firstOrNull()
            ?: throw Exception("User session not found. Please log in again.")
        val userId = user.id.toIntOrNull()
            ?: throw Exception("Invalid user ID. Please log in again.")

        Timber.d("Fetching group details for userId=%d", userId)

        val response = groupDetailsApi.getGroupDetails(GroupDetailsRequest(userId = userId))
        if (response.success != true) throw Exception("Failed to load group schedule.")
        response.toActivitySlots()
    }.onFailure { Timber.e(it, "Failed to fetch group details") }
}

package com.example.yourswelnes.feature.home.data

import com.example.yourswelnes.core.datastore.AuthPreferencesDataStore
import com.example.yourswelnes.feature.home.data.api.GroupDetailsApi
import com.example.yourswelnes.feature.home.data.dto.GroupDetailsRequest
import com.example.yourswelnes.feature.home.data.mapper.toGroups
import com.example.yourswelnes.feature.home.model.ActivitySlot
import com.example.yourswelnes.feature.home.model.Group
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.firstOrNull
import timber.log.Timber

@Singleton
class GroupDetailsRepositoryImpl @Inject constructor(
    private val groupDetailsApi: GroupDetailsApi,
    private val authPreferences: AuthPreferencesDataStore
) : GroupDetailsRepository {

    @Volatile
    private var cachedGroups: List<Group>? = null

    override suspend fun fetchGroups(forceRefresh: Boolean): Result<List<Group>> {
        cachedGroups?.takeIf { !forceRefresh }?.let { return Result.success(it) }
        return runCatching {
            val user = authPreferences.cachedUser.firstOrNull()
                ?: throw Exception("User session not found. Please log in again.")
            val userId = user.id.toIntOrNull()
                ?: throw Exception("Invalid user ID. Please log in again.")
            Timber.d("Fetching group details from API")
            val response = groupDetailsApi.getGroupDetails(GroupDetailsRequest(userId = userId))
            if (response.success != true) {
                // Backend signals this user has no groups (e.g. error = "TG User not found").
                // Treat as an empty list, not a failure, so the camera stays blocked.
                Timber.w("No groups for user: ${response.error}")
                emptyList<Group>().also { cachedGroups = it }
            } else {
                response.toGroups().also { cachedGroups = it }
            }
        }.onFailure { Timber.e(it, "Failed to fetch groups") }
    }

    override suspend fun fetchGroupDetails(): Result<List<ActivitySlot>> =
        fetchGroups().map { groups -> groups.flatMap { it.activities } }

    override fun clearCache() {
        cachedGroups = null
        Timber.d("Group cache cleared")
    }
}

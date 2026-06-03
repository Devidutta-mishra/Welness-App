package com.example.yourswelnes.feature.home.data.repository

import com.example.yourswelnes.feature.home.data.remote.api.ClubApi
import com.example.yourswelnes.feature.home.data.remote.mapper.toDomain
import com.example.yourswelnes.feature.home.domain.model.ClubDetails
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

@Singleton
class ClubRepositoryImpl @Inject constructor(
    private val clubApi: ClubApi
) : ClubRepository {

    override suspend fun getClubDetails(): Result<ClubDetails> = runCatching {
        val response = clubApi.getClubDetails()
        
        // We consider it a success if we can map it to a valid domain model
        // with a non-fallback club name.
        val domain = response.toDomain()
        
        if (domain.clubName != "Club information unavailable") {
            domain
        } else {
            // If the mapper returned fallback, check if there's an error message in the response
            val errorMsg = response.message ?: "Club details not found in response"
            Timber.e("Club API error or missing data: %s", errorMsg)
            throw Exception(errorMsg)
        }
    }.onFailure {
        Timber.e(it, "Exception during getClubDetails")
    }
}

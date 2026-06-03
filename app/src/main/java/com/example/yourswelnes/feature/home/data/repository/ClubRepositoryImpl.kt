package com.example.yourswelnes.feature.home.data.repository

import com.example.yourswelnes.core.datastore.LocationPreferencesDataStore
import com.example.yourswelnes.feature.home.data.remote.api.ClubApi
import com.example.yourswelnes.feature.home.data.remote.mapper.toDomain
import com.example.yourswelnes.feature.home.domain.model.ClubDetails
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

@Singleton
class ClubRepositoryImpl @Inject constructor(
    private val clubApi: ClubApi,
    private val locationPrefs: LocationPreferencesDataStore
) : ClubRepository {

    override suspend fun getClubDetails(): Result<ClubDetails> = runCatching {
        val response = clubApi.getClubDetails()
        Timber.d("Club API Response: %s", response)
        val domain = response.toDomain()
        if (response.success == true && domain.clubName.isNotBlank()) {
            locationPrefs.saveClubInfo(domain.id, domain.latitude, domain.longitude)
            domain
        } else {
            val errorMsg = response.message ?: "Club information not found"
            Timber.e("Club API error: %s", errorMsg)
            throw Exception(errorMsg)
        }
    }.onFailure {
        Timber.e(it, "Exception during getClubDetails")
    }
}

package com.example.yourswelnes.feature.home.data

import com.example.yourswelnes.core.datastore.LocationPreferencesDataStore
import com.example.yourswelnes.core.monitoring.CrashReporter
import com.example.yourswelnes.feature.home.data.api.ClubApi
import com.example.yourswelnes.feature.home.data.mapper.toDomain
import com.example.yourswelnes.feature.home.model.ClubDetails
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

@Singleton
class ClubRepositoryImpl @Inject constructor(
    private val clubApi: ClubApi,
    private val locationPrefs: LocationPreferencesDataStore
) : ClubRepository {

    override suspend fun getClubDetails(): Result<ClubDetails> {
        return try {
            val response = clubApi.getClubDetails()
            Timber.d("Club API Response: %s", response)
            val domain = response.toDomain()

            if (response.success == true && domain.clubName.isNotBlank()) {
                // Valid club from server — cache it as the new source of truth.
                locationPrefs.saveClubInfo(domain.id, domain.latitude, domain.longitude, domain.clubName)
                CrashReporter.setClubId(domain.id)
                Result.success(domain)
            } else {
                // Server EXPLICITLY reports no valid club for this user (reachable, but no club
                // assigned). This is a genuine "no club" state — clear stale info so the service
                // does not keep collecting against a previous club. Distinct from offline.
                locationPrefs.clearClubInfo()
                val errorMsg = response.message ?: "Club information not found"
                Timber.e("Club API error: %s", errorMsg)
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            // Network failure / offline (or a transient HTTP error). Do NOT clear cached club
            // info — the club is still assigned, the server just couldn't be reached. Fall back
            // to the last valid club cached in DataStore so the user keeps seeing their assigned
            // club and the foreground service keeps collecting (collection must work offline).
            val cached = cachedClubOrNull()
            if (cached != null) {
                Timber.w(e, "getClubDetails offline/failed — falling back to cached club '%s'", cached.clubName)
                Result.success(cached)
            } else {
                Timber.e(e, "getClubDetails failed and no cached club is available")
                Result.failure(e)
            }
        }
    }

    /** Reads the last-saved club from DataStore, or null if none was ever cached. */
    private suspend fun cachedClubOrNull(): ClubDetails? {
        val name = locationPrefs.getClubName()
        if (name.isNullOrBlank()) return null
        return ClubDetails(
            id = locationPrefs.getClubId() ?: 0,
            clubName = name,
            latitude = locationPrefs.getClubLatitude() ?: 0.0,
            longitude = locationPrefs.getClubLongitude() ?: 0.0
        )
    }
}

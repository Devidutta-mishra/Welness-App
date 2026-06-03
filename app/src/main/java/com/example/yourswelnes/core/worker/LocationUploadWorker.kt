package com.example.yourswelnes.core.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.yourswelnes.core.datastore.AuthPreferencesDataStore
import com.example.yourswelnes.core.datastore.LocationPreferencesDataStore
import com.example.yourswelnes.feature.location.data.remote.api.LocationApi
import com.example.yourswelnes.feature.location.data.remote.dto.LocationItemDto
import com.example.yourswelnes.feature.location.data.remote.dto.LocationUploadRequestDto
import com.example.yourswelnes.feature.location.data.repository.LocationRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.firstOrNull
import timber.log.Timber

@HiltWorker
class LocationUploadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val locationRepository: LocationRepository,
    private val locationApi: LocationApi,
    private val locationPrefs: LocationPreferencesDataStore,
    private val authPrefs: AuthPreferencesDataStore
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val pending = locationRepository.getPendingLocations()
        if (pending.isEmpty()) {
            Timber.d("No pending locations to upload")
            return Result.success()
        }

        val userId = authPrefs.cachedUser.firstOrNull()?.id ?: run {
            Timber.w("No cached user — skipping upload")
            return Result.success()
        }

        val clubId = locationPrefs.getClubId() ?: run {
            Timber.w("Club ID not cached — skipping upload")
            return Result.success()
        }

        val payload = LocationUploadRequestDto(
            locations = pending.map { record ->
                LocationItemDto(
                    userId = userId,
                    latitude = record.latitude,
                    longitude = record.longitude,
                    clubId = clubId,
                    distance = record.distance.toInt()
                )
            }
        )

        return try {
            val response = locationApi.storeLocations(payload)
            if (response.success == true) {
                locationRepository.markAsUploaded(pending.map { it.id })
                locationPrefs.saveLastSyncTime(System.currentTimeMillis())
                Timber.d("Uploaded ${pending.size} location record(s)")
                Result.success()
            } else {
                Timber.w("Upload API returned success=false: ${response.message}")
                Result.retry()
            }
        } catch (e: Exception) {
            Timber.e(e, "Location upload failed — will retry")
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "location_upload_periodic"

        fun schedule(workManager: WorkManager) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<LocationUploadWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .build()

            workManager.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
            Timber.d("LocationUploadWorker scheduled (15 min, network required)")
        }
    }
}

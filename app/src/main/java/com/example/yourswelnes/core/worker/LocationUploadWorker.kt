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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.firstOrNull
import timber.log.Timber

private val TIMESTAMP_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

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
        Timber.tag(TAG).d("doWork started — querying pending locations")
        val pending = locationRepository.getPendingLocations()
        if (pending.isEmpty()) {
            Timber.tag(TAG).d("No pending locations to upload — nothing to do")
            return Result.success()
        }
        Timber.tag(TAG).d("Found ${pending.size} pending location record(s) to upload")

        val userId = authPrefs.cachedUser.firstOrNull()?.id ?: run {
            Timber.tag(TAG).w("No cached user — cannot upload, skipping")
            return Result.success()
        }

        val clubId = locationPrefs.getClubId() ?: run {
            Timber.tag(TAG).w("Club ID not in DataStore — cannot upload, skipping")
            return Result.success()
        }

        Timber.tag(TAG).d("Preparing upload batch: userId=$userId, clubId=$clubId, count=${pending.size}")

        val payload = LocationUploadRequestDto(
            locations = pending.map { record ->
                LocationItemDto(
                    userId = userId,
                    latitude = record.latitude,
                    longitude = record.longitude,
                    clubId = clubId,
                    distance = record.distance.toInt(),
                    time = TIMESTAMP_FORMAT.format(Date(record.createdAt))
                )
            }
        )

        return try {
            Timber.tag(TAG).d("Posting ${pending.size} location(s) to /api/store-location")
            val response = locationApi.storeLocations(payload)
            if (response.success == true) {
                locationRepository.markAsUploaded(pending.map { it.id })
                locationPrefs.saveLastSyncTime(System.currentTimeMillis())
                Timber.tag(TAG).i("Upload SUCCESS — ${pending.size} record(s) marked uploaded. Message: ${response.message}")
                Result.success()
            } else {
                Timber.tag(TAG).w("Upload API returned success=false: ${response.message} — will retry")
                Result.retry()
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Upload FAILED (network/server error) — will retry with backoff")
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "LocationUploadWorker"
        private const val WORK_NAME = "location_upload_periodic"

        fun cancel(workManager: WorkManager) {
            workManager.cancelUniqueWork(WORK_NAME)
            Timber.tag(TAG).d("Periodic upload worker cancelled")
        }

        fun schedule(workManager: WorkManager) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            // WorkManager minimum periodic interval is 15 min; backend's uploadIntervalMinutes
            // may be lower (e.g. 10 min) but cannot go below the platform floor.
            val request = PeriodicWorkRequestBuilder<LocationUploadWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .build()

            workManager.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
            Timber.tag(TAG).d("Scheduled periodic upload worker (15 min interval, network required)")
        }
    }
}

package com.example.yourswelnes.core.location

import com.example.yourswelnes.core.datastore.AuthPreferencesDataStore
import com.example.yourswelnes.core.datastore.LocationPreferencesDataStore
import com.example.yourswelnes.feature.location.data.remote.api.LocationApi
import com.example.yourswelnes.feature.location.data.remote.dto.LocationItemDto
import com.example.yourswelnes.feature.location.data.remote.dto.LocationUploadRequestDto
import com.example.yourswelnes.feature.location.data.repository.LocationRepository
import com.example.yourswelnes.feature.location.domain.model.LocationRecord
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

private const val TAG = "LocationUploader"

// One backend request carries at most this many points. Keeps each payload small, bounds memory
// to a single batch, and keeps every markAsUploaded() list far under SQLite's parameter limit.
private const val BATCH_SIZE = 200

private val TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

/**
 * Single owner of the location-upload pipeline, shared by the in-service upload loop and the
 * WorkManager worker so the batching/marking/purging logic exists in exactly one place.
 *
 * Behaviour preserved from the previous inline implementations: same payload shape, same
 * collection-timestamp passthrough, same user/club resolution, same last-sync bookkeeping.
 *
 * What changed (the scalability fix):
 *  - Pending rows are drained in [BATCH_SIZE] batches instead of one unbounded request.
 *  - Each batch is marked uploaded only after the server confirms it, so a mid-drain failure
 *    never re-sends rows the backend already accepted.
 *  - Confirmed rows are purged so the table stays bounded over long-term use.
 */
@Singleton
class LocationUploader @Inject constructor(
    private val locationRepository: LocationRepository,
    private val locationApi: LocationApi,
    private val locationPrefs: LocationPreferencesDataStore,
    private val authPrefs: AuthPreferencesDataStore,
    private val uploadLock: LocationUploadLock
) {

    enum class Result { SUCCESS, NOTHING_TO_DO, FAILED }

    /**
     * Drains all pending location rows for the signed-in user. Holds [uploadLock] so the service
     * loop and the worker can never upload concurrently. Stops at the first failed batch and
     * reports [Result.FAILED] so the caller can retry; already-confirmed batches are not re-sent.
     */
    suspend fun uploadPending(): Result = uploadLock.mutex.withLock {
        val userId = authPrefs.cachedUser.firstOrNull()?.id ?: run {
            Timber.tag(TAG).w("No cached user — cannot upload, skipping")
            return@withLock Result.NOTHING_TO_DO
        }
        val clubId = locationPrefs.getClubId() ?: run {
            Timber.tag(TAG).w("Club ID not in DataStore — cannot upload, skipping")
            return@withLock Result.NOTHING_TO_DO
        }

        var uploadedBatches = 0
        var failed = false
        // Guards against a stuck loop: if a batch fails to mark, the same rows would be fetched
        // again forever. If the oldest pending id doesn't advance between iterations, we bail.
        var lastFirstId = -1L

        while (true) {
            val batch = locationRepository.getPendingLocations(userId, BATCH_SIZE)
            if (batch.isEmpty()) break

            if (batch.first().id == lastFirstId) {
                Timber.tag(TAG).e("Upload made no progress (mark failed?) — aborting to avoid resend loop")
                failed = true
                break
            }
            lastFirstId = batch.first().id

            val ids = batch.map { it.id }
            Timber.tag(TAG).i(
                "UPLOAD BATCH | userId=$userId | count=${batch.size} | ids=$ids | " +
                    "range=${batch.first().formattedTime()} -> ${batch.last().formattedTime()}"
            )

            val payload = LocationUploadRequestDto(
                locations = batch.map { record ->
                    // record.timestamp is the original collection instant; transmit it as-is.
                    val storedTime = record.formattedTime()
                    Timber.tag(TAG).d("LOCATION UPLOAD | id=${record.id} | storedTime=$storedTime")
                    LocationItemDto(
                        userId = userId,
                        latitude = record.latitude,
                        longitude = record.longitude,
                        clubId = clubId,
                        distance = record.distance.toInt(),
                        time = storedTime
                    )
                }
            )

            val response = runCatching { locationApi.storeLocations(payload) }.getOrElse { e ->
                Timber.tag(TAG).e(e, "Upload FAILED (network/server error) — will retry")
                failed = true
                null
            }
            if (response == null) break
            if (response.success != true) {
                Timber.tag(TAG).w("Upload API returned success=false: ${response.message} — will retry")
                failed = true
                break
            }

            locationRepository.markAsUploaded(ids)
            uploadedBatches++
            Timber.tag(TAG).i("UPLOAD SUCCESS | userId=$userId | markedUploaded=$ids")
        }

        if (uploadedBatches > 0) {
            locationPrefs.saveLastSyncTime(System.currentTimeMillis())
        }
        // Reclaim space from everything confirmed this run (and any leftovers from prior runs).
        locationRepository.purgeUploadedLocations()

        when {
            failed -> Result.FAILED
            uploadedBatches == 0 -> Result.NOTHING_TO_DO
            else -> Result.SUCCESS
        }
    }

    private fun LocationRecord.formattedTime(): String =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault())
            .format(TIMESTAMP_FORMATTER)
}

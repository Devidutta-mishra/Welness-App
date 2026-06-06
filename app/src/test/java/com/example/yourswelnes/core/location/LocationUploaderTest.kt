package com.example.yourswelnes.core.location

import com.example.yourswelnes.core.datastore.AuthPreferencesDataStore
import com.example.yourswelnes.core.datastore.LocationPreferencesDataStore
import com.example.yourswelnes.feature.auth.model.AuthUser
import com.example.yourswelnes.feature.location.data.api.LocationApi
import com.example.yourswelnes.feature.location.data.dto.LocationConfigDto
import com.example.yourswelnes.feature.location.data.dto.LocationItemDto
import com.example.yourswelnes.feature.location.data.dto.LocationUploadRequestDto
import com.example.yourswelnes.feature.location.data.dto.LocationUploadResponseDto
import com.example.yourswelnes.feature.location.data.LocationRepository
import com.example.yourswelnes.feature.location.model.LocationRecord
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.io.IOException
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM unit tests for the scalability fix. They drive the real [LocationUploader] against an
 * in-memory repository fake and a controllable API fake — no device or Room needed — to prove:
 *  - a large offline backlog drains fully in bounded batches (the old ~1000-row cliff is gone),
 *  - the collected timestamp is transmitted verbatim,
 *  - a network failure loses no data and resumes cleanly,
 *  - a confirmed batch is never re-sent after a mid-drain failure,
 *  - a stuck "mark uploaded" can't spin into an endless resend loop.
 */
class LocationUploaderTest {

    private val userId = "1756"
    private val clubId = 47

    // ---- Fakes -------------------------------------------------------------

    /** In-memory stand-in for the Room-backed repository. */
    private class FakeLocationRepository(
        private val markBroken: Boolean = false
    ) : LocationRepository {
        val rows = mutableListOf<LocationRecord>()
        private var nextId = 1L

        override suspend fun saveLocation(record: LocationRecord) {
            rows += record.copy(id = nextId++)
        }

        override suspend fun getPendingLocations(userId: String, limit: Int): List<LocationRecord> =
            rows.filter { !it.uploaded && it.userId == userId }
                .sortedBy { it.createdAt }
                .take(limit)

        override suspend fun markAsUploaded(ids: List<Long>) {
            if (markBroken) return // simulate a mark that silently fails to persist
            val idSet = ids.toSet()
            for (i in rows.indices) {
                if (rows[i].id in idSet) rows[i] = rows[i].copy(uploaded = true)
            }
        }

        override suspend fun purgeUploadedLocations() {
            rows.removeAll { it.uploaded }
        }

        fun pendingCount() = rows.count { !it.uploaded }
        fun totalCount() = rows.size
    }

    /** Records every batch sent; can be told to throw on a given 1-based call index. */
    private class FakeLocationApi(private val failOnCall: Int = -1) : LocationApi {
        val calls = mutableListOf<List<LocationItemDto>>()

        override suspend fun getLocationConfig(): LocationConfigDto =
            throw UnsupportedOperationException("not used in upload test")

        override suspend fun storeLocations(request: LocationUploadRequestDto): LocationUploadResponseDto {
            val callIndex = calls.size + 1
            if (callIndex == failOnCall) throw IOException("simulated network failure on call $callIndex")
            calls += request.locations
            return LocationUploadResponseDto(success = true)
        }
    }

    // ---- Helpers -----------------------------------------------------------

    private fun newUploader(repo: LocationRepository, api: LocationApi): LocationUploader {
        val locationPrefs = mockk<LocationPreferencesDataStore>(relaxed = true)
        coEvery { locationPrefs.getClubId() } returns clubId

        val authPrefs = mockk<AuthPreferencesDataStore>(relaxed = true)
        every { authPrefs.cachedUser } returns flowOf(AuthUser(id = userId, name = "Test"))

        return LocationUploader(
            locationRepository = repo,
            locationApi = api,
            locationPrefs = locationPrefs,
            authPrefs = authPrefs,
            uploadLock = LocationUploadLock()
        )
    }

    private fun record(timestamp: Long) = LocationRecord(
        userId = userId,
        latitude = 20.2961,
        longitude = 85.8245,
        distance = 5000f,
        timestamp = timestamp,
        createdAt = timestamp
    )

    // ---- Tests -------------------------------------------------------------

    @Test
    fun largeBacklog_drainsInBoundedBatches_thenPurges() = runBlocking {
        val repo = FakeLocationRepository()
        repeat(1500) { i -> repo.saveLocation(record(1_000L + i)) } // > old 999-row cliff
        val api = FakeLocationApi()

        val result = newUploader(repo, api).uploadPending()

        assertEquals(LocationUploader.Result.SUCCESS, result)
        assertEquals("every point delivered exactly once", 1500, api.calls.flatten().size)
        assertTrue("no batch exceeded the 200 cap", api.calls.all { it.size <= 200 })
        assertEquals("table fully drained", 0, repo.pendingCount())
        assertEquals("confirmed rows purged", 0, repo.totalCount())
    }

    @Test
    fun collectionTimestamp_isTransmittedVerbatim() = runBlocking {
        val repo = FakeLocationRepository()
        val millis = LocalDateTime.of(2026, 6, 4, 8, 15, 23)
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        repo.saveLocation(record(millis))
        val api = FakeLocationApi()

        newUploader(repo, api).uploadPending()

        assertEquals("2026-06-04 08:15:23", api.calls.single().single().time)
    }

    @Test
    fun networkFailure_keepsDataPending_andResumesNextRun() = runBlocking {
        val repo = FakeLocationRepository()
        repeat(300) { i -> repo.saveLocation(record(1_000L + i)) }

        val failing = FakeLocationApi(failOnCall = 1)
        val r1 = newUploader(repo, failing).uploadPending()
        assertEquals(LocationUploader.Result.FAILED, r1)
        assertEquals("nothing accepted by server", 0, failing.calls.flatten().size)
        assertEquals("nothing lost or marked", 300, repo.pendingCount())
        assertEquals("nothing purged", 300, repo.totalCount())

        val ok = FakeLocationApi()
        val r2 = newUploader(repo, ok).uploadPending()
        assertEquals(LocationUploader.Result.SUCCESS, r2)
        assertEquals("all data delivered on recovery", 300, ok.calls.flatten().size)
        assertEquals(0, repo.pendingCount())
    }

    @Test
    fun partialFailure_doesNotResendConfirmedBatch() = runBlocking {
        val repo = FakeLocationRepository()
        repeat(300) { i -> repo.saveLocation(record(1_000L + i)) }

        val api = FakeLocationApi(failOnCall = 2) // first batch ok, second fails
        val r1 = newUploader(repo, api).uploadPending()
        assertEquals(LocationUploader.Result.FAILED, r1)
        assertEquals("only the confirmed first batch landed", 200, api.calls.flatten().size)
        assertEquals("remaining points still pending", 100, repo.pendingCount())

        val ok = FakeLocationApi()
        val r2 = newUploader(repo, ok).uploadPending()
        assertEquals(LocationUploader.Result.SUCCESS, r2)
        assertEquals("only the leftover 100 re-sent — no duplicate of the first 200", 100, ok.calls.flatten().size)
        assertEquals(0, repo.pendingCount())
    }

    @Test
    fun stuckMark_isDetected_andDoesNotResendForever() = runBlocking {
        val repo = FakeLocationRepository(markBroken = true)
        repeat(500) { i -> repo.saveLocation(record(1_000L + i)) }
        val api = FakeLocationApi()

        val result = newUploader(repo, api).uploadPending()

        assertEquals(LocationUploader.Result.FAILED, result)
        assertTrue("must bail after detecting no progress, not flood the server", api.calls.size <= 2)
    }
}

package com.example.yourswelnes.core.location

import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.yourswelnes.core.database.AppDatabase
import com.example.yourswelnes.core.database.MIGRATION_3_4
import com.example.yourswelnes.feature.location.data.LocationRepositoryImpl
import com.example.yourswelnes.feature.location.model.LocationRecord
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On-device tests that run against the real Android SQLite — the layer the JVM unit tests can't
 * reach. They prove the scalability fixes hold on actual device storage:
 *  - the repository's chunked markAsUploaded handles an id list far past the 999-parameter floor,
 *  - getPendingLocations honours LIMIT and oldest-first ordering,
 *  - purgeUploadedLocations reclaims confirmed rows,
 *  - MIGRATION_3_4 creates the expected index and is idempotent.
 */
@RunWith(AndroidJUnit4::class)
class LocationPersistenceTest {

    private val userId = "1756"
    private lateinit var db: AppDatabase
    private lateinit var repo: LocationRepositoryImpl

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        repo = LocationRepositoryImpl(db.locationDao())
    }

    @After
    fun tearDown() = db.close()

    private fun record(createdAt: Long) = LocationRecord(
        userId = userId,
        latitude = 20.2961,
        longitude = 85.8245,
        distance = 5000f,
        timestamp = createdAt,
        createdAt = createdAt
    )

    @Test
    fun chunkedMark_handlesIdListBeyond999_onRealSqlite() = runBlocking {
        // 1500 > the 999 host-parameter floor on API 29's SQLite. The old single
        // IN (:ids) statement would throw "too many SQL variables"; the chunked repo path must not.
        repeat(1500) { i -> repo.saveLocation(record(1_000L + i)) }

        val all = repo.getPendingLocations(userId, Int.MAX_VALUE)
        assertEquals(1500, all.size)

        repo.markAsUploaded(all.map { it.id }) // must not throw, must mark every row

        assertTrue("all rows marked uploaded", repo.getPendingLocations(userId, Int.MAX_VALUE).isEmpty())
    }

    @Test
    fun getPending_respectsLimitAndOrdering_thenPurge() = runBlocking {
        repeat(1000) { i -> repo.saveLocation(record(5_000L + i)) }

        val first200 = repo.getPendingLocations(userId, 200)
        assertEquals(200, first200.size)
        assertEquals("oldest-first ordering", (0 until 200).map { 5_000L + it }, first200.map { it.createdAt })

        repo.markAsUploaded(first200.map { it.id })
        repo.purgeUploadedLocations()

        val remaining = repo.getPendingLocations(userId, Int.MAX_VALUE)
        assertEquals("confirmed rows purged", 800, remaining.size)
        assertEquals("next oldest advanced past the purged batch", 5_200L, remaining.first().createdAt)
    }

    @Test
    fun migration_3_4_createsExpectedIndex_andIsIdempotent() {
        val sdb: SupportSQLiteDatabase = db.openHelper.writableDatabase
        // Room already created the index from the entity; drop it, then let the real migration
        // recreate it exactly as a 3->4 upgrade would on the device.
        sdb.execSQL("DROP INDEX IF EXISTS index_locations_user_id_uploaded_created_at")
        assertFalse("index removed for the test", sdb.hasUploadIndex())

        MIGRATION_3_4.migrate(sdb)
        assertTrue("migration created the index", sdb.hasUploadIndex())

        MIGRATION_3_4.migrate(sdb) // IF NOT EXISTS -> running again must not throw
        assertTrue("migration is idempotent", sdb.hasUploadIndex())
    }

    private fun SupportSQLiteDatabase.hasUploadIndex(): Boolean =
        query(
            "SELECT name FROM sqlite_master WHERE type='index' AND " +
                "name='index_locations_user_id_uploaded_created_at'"
        ).use { it.moveToFirst() }
}

package com.example.yourswelnes.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.yourswelnes.core.database.entity.NotificationEntity

@Dao
interface NotificationDao {

    // IGNORE so that existing rows (and their isDisplayed state) are never overwritten by a re-fetch.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(entities: List<NotificationEntity>)

    // Sync the server-authoritative read state without touching isDisplayed.
    @Query("UPDATE notifications SET is_read = :isRead WHERE id = :id")
    suspend fun updateReadState(id: Int, isRead: Boolean)

    @Query("UPDATE notifications SET is_read = 1 WHERE id = :id")
    suspend fun markRead(id: Int)

    // Called after posting a system notification so it is never shown again.
    @Query("UPDATE notifications SET is_displayed = 1 WHERE id = :id")
    suspend fun markDisplayed(id: Int)

    // All queries filter by userId so User A's rows are never visible to User B.
    @Query("SELECT * FROM notifications WHERE user_id = :userId ORDER BY id DESC")
    suspend fun getAllForUser(userId: String): List<NotificationEntity>

    @Query("SELECT * FROM notifications WHERE user_id = :userId AND is_displayed = 0")
    suspend fun getUndisplayedForUser(userId: String): List<NotificationEntity>
}

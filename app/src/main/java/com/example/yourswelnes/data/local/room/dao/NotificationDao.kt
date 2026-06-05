package com.example.yourswelnes.data.local.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.yourswelnes.data.local.room.entity.NotificationEntity

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

    @Query("SELECT * FROM notifications ORDER BY id DESC")
    suspend fun getAll(): List<NotificationEntity>

    // Entries that need a system notification: never shown before, regardless of read state.
    @Query("SELECT * FROM notifications WHERE is_displayed = 0")
    suspend fun getUndisplayed(): List<NotificationEntity>
}

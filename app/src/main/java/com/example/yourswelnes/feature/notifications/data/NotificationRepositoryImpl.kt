package com.example.yourswelnes.feature.notifications.data

import com.example.yourswelnes.core.datastore.AuthPreferencesDataStore
import com.example.yourswelnes.core.database.dao.NotificationDao
import com.example.yourswelnes.core.database.entity.NotificationEntity
import com.example.yourswelnes.feature.notifications.data.api.NotificationApi
import com.example.yourswelnes.feature.notifications.data.dto.MarkReadRequestDto
import com.example.yourswelnes.feature.notifications.data.mapper.toDomain
import com.example.yourswelnes.feature.notifications.model.NotificationItem
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import timber.log.Timber

private const val TAG = "NotificationRepo"

@Singleton
class NotificationRepositoryImpl @Inject constructor(
    private val notificationApi: NotificationApi,
    private val notificationDao: NotificationDao,
    private val authPrefs: AuthPreferencesDataStore
) : NotificationRepository {

    private val _notifications = MutableStateFlow<List<NotificationItem>>(emptyList())
    override val notifications: StateFlow<List<NotificationItem>> = _notifications.asStateFlow()

    override suspend fun fetchNotifications(limit: Int, offset: Int): Result<List<NotificationItem>> =
        runCatching {
            val userId = resolveUserId() ?: return@runCatching emptyList()

            val items = notificationApi.getNotifications(limit, offset)
                .notifications
                ?.map { it.toDomain() }
                ?: emptyList()

            // 1. Insert rows owned by this user (IGNORE preserves existing entries).
            notificationDao.insertAll(items.map { it.toEntity(userId) })

            // 2. Sync server-authoritative read state for every returned row.
            items.forEach { notificationDao.updateReadState(it.id, it.isRead) }

            // 3. Emit only this user's notifications (never another user's rows).
            // Tray notifications are now delivered exclusively by FCM (YwFirebaseMessagingService).
            val merged = notificationDao.getAllForUser(userId).map { it.toDomain() }
            _notifications.value = merged
            Timber.tag(TAG).d("SESSION USER=$userId | loaded ${merged.size} notifications")
            merged
        }.onFailure { Timber.tag(TAG).e(it, "fetchNotifications failed") }

    override suspend fun markAsRead(notificationId: Int): Result<Unit> {
        val current = _notifications.value.find { it.id == notificationId }
        if (current?.isRead == true) {
            Timber.tag(TAG).d("Notification $notificationId already read — skipping")
            return Result.success(Unit)
        }

        val snapshot = _notifications.value
        _notifications.update { list ->
            list.map { if (it.id == notificationId) it.copy(isRead = true) else it }
        }

        return runCatching {
            val response = notificationApi.markAsRead(MarkReadRequestDto(notificationId))
            if (response.success != true) {
                throw Exception(response.message ?: "Failed to mark notification as read")
            }
            notificationDao.markRead(notificationId)
            Timber.tag(TAG).i("Notification $notificationId marked read on server and locally")
        }.onFailure { error ->
            Timber.tag(TAG).e(error, "markAsRead failed for id=$notificationId — reverting")
            _notifications.value = snapshot
        }
    }

    override suspend fun refreshNotifications(): Result<List<NotificationItem>> =
        fetchNotifications(limit = 10, offset = 0)

    override fun getUnreadCount(notifications: List<NotificationItem>): Int =
        notifications.count { !it.isRead }

    /** Wipes the in-memory list on logout so User A's notifications are never shown to User B. */
    override fun clearCache() {
        _notifications.value = emptyList()
        Timber.tag(TAG).i("Notification cache cleared (session logout)")
    }

    private suspend fun resolveUserId(): String? {
        val userId = authPrefs.cachedUser.firstOrNull()?.id
        if (userId.isNullOrBlank()) {
            Timber.tag(TAG).w("No authenticated user — skipping notification fetch")
        }
        return userId?.takeIf { it.isNotBlank() }
    }

    private fun NotificationItem.toEntity(userId: String) = NotificationEntity(
        id = id,
        userId = userId,
        title = title,
        message = message,
        type = type,
        isRead = isRead,
        isDisplayed = false,
        createdAt = createdAt
    )

    private fun NotificationEntity.toDomain() = NotificationItem(
        id = id,
        title = title,
        message = message,
        type = type,
        isRead = isRead,
        createdAt = createdAt
    )
}

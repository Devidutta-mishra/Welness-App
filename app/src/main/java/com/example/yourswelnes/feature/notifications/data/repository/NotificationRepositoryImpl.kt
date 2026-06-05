package com.example.yourswelnes.feature.notifications.data.repository

import com.example.yourswelnes.core.notification.AppNotificationManager
import com.example.yourswelnes.data.local.room.dao.NotificationDao
import com.example.yourswelnes.data.local.room.entity.NotificationEntity
import com.example.yourswelnes.feature.notifications.data.remote.api.NotificationApi
import com.example.yourswelnes.feature.notifications.data.remote.dto.MarkReadRequestDto
import com.example.yourswelnes.feature.notifications.data.remote.mapper.toDomain
import com.example.yourswelnes.feature.notifications.domain.model.NotificationItem
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import timber.log.Timber

private const val TAG = "NotificationRepo"

@Singleton
class NotificationRepositoryImpl @Inject constructor(
    private val notificationApi: NotificationApi,
    private val notificationDao: NotificationDao,
    private val appNotificationManager: AppNotificationManager
) : NotificationRepository {

    private val _notifications = MutableStateFlow<List<NotificationItem>>(emptyList())
    override val notifications: StateFlow<List<NotificationItem>> = _notifications.asStateFlow()

    override suspend fun fetchNotifications(limit: Int, offset: Int): Result<List<NotificationItem>> =
        runCatching {
            val items = notificationApi.getNotifications(limit, offset)
                .notifications
                ?.map { it.toDomain() }
                ?: emptyList()

            // 1. Insert new rows only (IGNORE preserves isDisplayed for existing entries).
            notificationDao.insertAll(items.map { it.toEntity() })

            // 2. Sync server-authoritative read state for every returned row.
            items.forEach { notificationDao.updateReadState(it.id, it.isRead) }

            // 3. Post a system notification for every entry not yet displayed.
            //    This is the infinite-loop guard: once displayed it is never shown again.
            val undisplayed = notificationDao.getUndisplayed()
            undisplayed.forEach { entity ->
                appNotificationManager.show(entity.id, entity.title, entity.message)
                notificationDao.markDisplayed(entity.id)
                Timber.tag(TAG).i("System notification shown: id=${entity.id}, title=${entity.title}")
            }

            // 4. Emit the fresh list (now from DB so isDisplayed state is authoritative).
            val merged = notificationDao.getAll().map { it.toDomain() }
            _notifications.value = merged
            merged
        }.onFailure { Timber.tag(TAG).e(it, "fetchNotifications failed") }

    override suspend fun markAsRead(notificationId: Int): Result<Unit> {
        // Guard: skip if already read to avoid duplicate API calls.
        val current = _notifications.value.find { it.id == notificationId }
        if (current?.isRead == true) {
            Timber.tag(TAG).d("Notification $notificationId already read — skipping")
            return Result.success(Unit)
        }

        val snapshot = _notifications.value
        // Optimistic update so both screens reflect the change immediately.
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

    private fun NotificationItem.toEntity() = NotificationEntity(
        id = id,
        title = title,
        message = message,
        type = type,
        isRead = isRead,
        isDisplayed = false,   // default; IGNORE conflict preserves existing isDisplayed = true
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

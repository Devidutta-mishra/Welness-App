package com.example.yourswelnes.feature.notifications.data.repository

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

@Singleton
class NotificationRepositoryImpl @Inject constructor(
    private val notificationApi: NotificationApi
) : NotificationRepository {

    private val _notifications = MutableStateFlow<List<NotificationItem>>(emptyList())
    override val notifications: StateFlow<List<NotificationItem>> = _notifications.asStateFlow()

    override suspend fun fetchNotifications(limit: Int, offset: Int): Result<List<NotificationItem>> =
        runCatching {
            notificationApi.getNotifications(limit, offset)
                .notifications
                ?.map { it.toDomain() }
                ?: emptyList()
        }.onSuccess { list ->
            _notifications.value = list
        }.onFailure { Timber.e(it, "Failed to fetch notifications") }

    override suspend fun markAsRead(notificationId: Int): Result<Unit> {
        Timber.d("Marking notification as read: mappingId=$notificationId")
        // Snapshot for rollback on API failure
        val snapshot = _notifications.value
        // Optimistic update — flip immediately so both screens update at once
        _notifications.update { list ->
            list.map { if (it.id == notificationId) it.copy(isRead = true) else it }
        }
        return runCatching {
            val response = notificationApi.markAsRead(MarkReadRequestDto(notificationId))
            if (response.success != true) {
                throw Exception(response.message ?: "Failed to mark notification as read")
            }
            Timber.i("Notification $notificationId successfully marked as read in backend")
        }.onFailure { error ->
            Timber.e(error, "markAsRead failed for id=$notificationId — reverting local state")
            _notifications.value = snapshot
        }
    }

    override suspend fun refreshNotifications(): Result<List<NotificationItem>> =
        fetchNotifications(limit = 10, offset = 0)

    override fun getUnreadCount(notifications: List<NotificationItem>): Int =
        notifications.count { !it.isRead }
}

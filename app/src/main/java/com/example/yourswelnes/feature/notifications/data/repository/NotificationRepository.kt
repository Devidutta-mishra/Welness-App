package com.example.yourswelnes.feature.notifications.data.repository

import com.example.yourswelnes.feature.notifications.domain.model.NotificationItem
import kotlinx.coroutines.flow.StateFlow

interface NotificationRepository {
    val notifications: StateFlow<List<NotificationItem>>
    suspend fun fetchNotifications(limit: Int = 10, offset: Int = 0): Result<List<NotificationItem>>
    suspend fun markAsRead(notificationId: Int): Result<Unit>
    suspend fun refreshNotifications(): Result<List<NotificationItem>>
    fun getUnreadCount(notifications: List<NotificationItem>): Int
    /** Clears the in-memory notification list. Must be called on logout to prevent cross-user leakage. */
    fun clearCache()
}

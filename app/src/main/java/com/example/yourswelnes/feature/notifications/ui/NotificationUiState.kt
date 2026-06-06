package com.example.yourswelnes.feature.notifications.ui

import com.example.yourswelnes.feature.notifications.model.NotificationItem

data class NotificationUiState(
    val isLoading: Boolean = false,
    val notifications: List<NotificationItem> = emptyList(),
    val error: String? = null,
    val selectedNotification: NotificationItem? = null
) {
    val unreadCount: Int get() = notifications.count { !it.isRead }
}

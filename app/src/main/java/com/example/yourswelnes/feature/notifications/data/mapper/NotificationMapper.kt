package com.example.yourswelnes.feature.notifications.data.mapper

import com.example.yourswelnes.feature.notifications.data.dto.UserNotificationDto
import com.example.yourswelnes.feature.notifications.model.NotificationItem

fun UserNotificationDto.toDomain() = NotificationItem(
    id = id,                            // outer mapping ID — used for markAsRead
    title = notification.title,
    message = notification.message,
    type = notification.type,
    isRead = when (isRead) {
        is Boolean -> isRead
        is Number -> isRead.toInt() == 1
        is String -> isRead == "1" || isRead.lowercase() == "true"
        else -> false
    },
    createdAt = notification.createdAt ?: createdAt ?: ""
)

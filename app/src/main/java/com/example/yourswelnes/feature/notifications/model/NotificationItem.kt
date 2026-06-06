package com.example.yourswelnes.feature.notifications.model

data class NotificationItem(
    val id: Int,           // user-notification mapping ID — used for markAsRead
    val title: String,
    val message: String,
    val type: String,      // "warning", "info", etc. — never hardcoded in UI
    val isRead: Boolean,
    val createdAt: String  // ISO-8601 from backend
)

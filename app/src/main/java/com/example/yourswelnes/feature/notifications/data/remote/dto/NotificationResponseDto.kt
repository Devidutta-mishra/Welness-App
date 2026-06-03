package com.example.yourswelnes.feature.notifications.data.remote.dto

import com.google.gson.annotations.SerializedName

data class NotificationResponseDto(
    @SerializedName("success") val success: Boolean? = null,
    @SerializedName("notifications") val notifications: List<UserNotificationDto>? = null,
    @SerializedName("meta") val meta: NotificationMetaDto? = null
)

data class UserNotificationDto(
    @SerializedName("id") val id: Int,                           // user-notification mapping ID
    @SerializedName("user_id") val userId: Int? = null,
    @SerializedName("notification_id") val notificationId: Int,  // inner notification content ID
    @SerializedName("is_read") val isRead: Any? = null,          // Use Any to handle Int, Boolean, or String
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("updated_at") val updatedAt: String? = null,
    @SerializedName("notification") val notification: NotificationDto
)

data class NotificationDto(
    @SerializedName("id") val id: Int,
    @SerializedName("user_id") val userId: Int? = null,
    @SerializedName("title") val title: String,
    @SerializedName("message") val message: String,
    @SerializedName("type") val type: String,
    @SerializedName("status") val status: Int? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("updated_at") val updatedAt: String? = null
)

data class NotificationMetaDto(
    @SerializedName("limit") val limit: Int,
    @SerializedName("offset") val offset: Int
)

data class MarkReadRequestDto(
    @SerializedName("notification_id") val notificationId: Int  // must be the mapping ID, NOT the content ID
)

data class MarkReadResponseDto(
    @SerializedName("success") val success: Boolean? = null,
    @SerializedName("message") val message: String? = null
)

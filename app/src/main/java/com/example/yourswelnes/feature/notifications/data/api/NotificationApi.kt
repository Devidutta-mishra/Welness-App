package com.example.yourswelnes.feature.notifications.data.api

import com.example.yourswelnes.feature.notifications.data.dto.MarkReadRequestDto
import com.example.yourswelnes.feature.notifications.data.dto.MarkReadResponseDto
import com.example.yourswelnes.feature.notifications.data.dto.NotificationResponseDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface NotificationApi {

    @GET("api/notifications")
    suspend fun getNotifications(
        @Query("limit") limit: Int = 10,
        @Query("offset") offset: Int = 0
    ): NotificationResponseDto

    @POST("api/notifications/read")
    suspend fun markAsRead(@Body request: MarkReadRequestDto): MarkReadResponseDto
}

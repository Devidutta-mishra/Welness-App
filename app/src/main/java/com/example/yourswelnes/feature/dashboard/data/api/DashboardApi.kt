package com.example.yourswelnes.feature.dashboard.data.api

import com.example.yourswelnes.feature.dashboard.data.dto.GenerateRedirectUrlRequest
import com.example.yourswelnes.feature.dashboard.data.dto.GenerateRedirectUrlResponse
import retrofit2.http.Body
import retrofit2.http.POST

interface DashboardApi {

    @POST("api/generate-login-link")
    suspend fun generateRedirectUrl(
        @Body request: GenerateRedirectUrlRequest
    ): GenerateRedirectUrlResponse
}

package com.example.yourswelnes.feature.dashboard.data.remote.api

import com.example.yourswelnes.feature.dashboard.data.remote.dto.GenerateRedirectUrlRequest
import com.example.yourswelnes.feature.dashboard.data.remote.dto.GenerateRedirectUrlResponse
import retrofit2.http.Body
import retrofit2.http.POST

interface DashboardApi {

    @POST("api/generate-login-link")
    suspend fun generateRedirectUrl(
        @Body request: GenerateRedirectUrlRequest
    ): GenerateRedirectUrlResponse
}

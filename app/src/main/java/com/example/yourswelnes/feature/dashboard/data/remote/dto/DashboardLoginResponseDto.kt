package com.example.yourswelnes.feature.dashboard.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * Response from POST /api/login-url.
 * Resilient DTO that handles both top-level and nested URL fields.
 */
data class DashboardLoginResponseDto(
    @SerializedName("status") val status: Any? = null,
    @SerializedName("success") val success: Any? = null,
    @SerializedName("message") val message: String? = null,
    
    // Top-level fields
    @SerializedName("url") val url: String? = null,
    @SerializedName("login_url") val loginUrl: String? = null,
    @SerializedName("redirect_url") val redirectUrl: String? = null,
    @SerializedName("redirect") val redirect: String? = null,
    
    // Nested fields
    @SerializedName("data") val data: DashboardDataDto? = null
)

data class DashboardDataDto(
    @SerializedName("url") val url: String? = null,
    @SerializedName("login_url") val loginUrl: String? = null,
    @SerializedName("redirect_url") val redirectUrl: String? = null,
    @SerializedName("redirect") val redirect: String? = null
)

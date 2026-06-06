package com.example.yourswelnes.feature.dashboard.data.dto

import com.google.gson.annotations.SerializedName

data class GenerateRedirectUrlRequest(
    @SerializedName("userId") val userId: Int
)

data class GenerateRedirectUrlResponse(
    @SerializedName("success") val success: Boolean? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("magic_token") val magicToken: String? = null,
    @SerializedName("redirect_url") val redirectUrl: String? = null
)

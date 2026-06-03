package com.example.yourswelnes.feature.auth.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * Request body for `POST https://ywadvance.com/api/login`.
 *
 * Field names match the API contract in api_structure.md exactly.
 */
data class LoginRequestDto(
    @SerializedName("phone") val phone: String,
    @SerializedName("password") val password: String,
    @SerializedName("device_token") val deviceToken: String
)

package com.example.yourswelnes.feature.auth.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * Flexible User DTO to capture both 'id' and 'user_id' fields.
 * 'id' is often numeric, 'user_id' is often a string/slug (e.g., YW-11258).
 */
data class UserDto(
    @SerializedName("id") val id: Any? = null,
    @SerializedName("user_id") val userId: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("email") val email: String? = null,
    @SerializedName("phone") val phone: String? = null,
    @SerializedName("gender") val gender: String? = null,
    @SerializedName("role") val role: String? = null,
    @SerializedName("status") val status: String? = null,
    @SerializedName("level") val level: String? = null,
    @SerializedName("image") val image: String? = null
)

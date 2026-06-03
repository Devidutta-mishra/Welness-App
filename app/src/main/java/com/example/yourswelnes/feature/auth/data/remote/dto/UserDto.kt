package com.example.yourswelnes.feature.auth.data.remote.dto

import com.google.gson.annotations.SerializedName

data class UserDto(
    @SerializedName("user_id") val id: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("email") val email: String? = null,
    @SerializedName("phone") val phone: String? = null,
    @SerializedName("gender") val gender: String? = null,
    @SerializedName("role") val role: String? = null,
    @SerializedName("status") val status: String? = null,
    @SerializedName("level") val level: String? = null,
    @SerializedName("image") val image: String? = null
)

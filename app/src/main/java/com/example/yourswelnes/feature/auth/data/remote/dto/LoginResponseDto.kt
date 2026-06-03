package com.example.yourswelnes.feature.auth.data.remote.dto

import com.google.gson.annotations.SerializedName

data class LoginResponseDto(
    @SerializedName("status") val status: Int? = null,
    @SerializedName("token") val token: String? = null,
    @SerializedName("user") val user: UserDto? = null,
    @SerializedName("profile_image") val profileImage: String? = null,
    @SerializedName("redirect") val redirect: String? = null
)

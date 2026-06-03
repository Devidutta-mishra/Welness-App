package com.example.yourswelnes.feature.dashboard.data.remote.dto

import com.google.gson.annotations.SerializedName

data class DashboardLoginRequestDto(
    @SerializedName("token") val token: String
)

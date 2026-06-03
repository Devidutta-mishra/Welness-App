package com.example.yourswelnes.feature.home.data.remote.dto

import com.google.gson.annotations.SerializedName

data class ClubDetailsResponseDto(
    @SerializedName("success") val success: Boolean? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("data") val data: List<ClubDetailsDataDto>? = null
)

data class ClubDetailsDataDto(
    @SerializedName("id") val id: Int? = null,
    @SerializedName("club_name") val clubName: String? = null,
    @SerializedName("latitude") val latitude: Double? = null,
    @SerializedName("longitude") val longitude: Double? = null
)

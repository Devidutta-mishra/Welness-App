package com.example.yourswelnes.feature.location.data.remote.dto

import com.google.gson.annotations.SerializedName

data class LocationUploadRequestDto(
    @SerializedName("locations") val locations: List<LocationItemDto>
)

data class LocationItemDto(
    @SerializedName("user_id") val userId: String,
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double,
    @SerializedName("club_id") val clubId: Int,
    @SerializedName("distance") val distance: Int,
    @SerializedName("time") val time: String
)

data class LocationUploadResponseDto(
    @SerializedName("success") val success: Boolean? = null,
    @SerializedName("message") val message: String? = null
)

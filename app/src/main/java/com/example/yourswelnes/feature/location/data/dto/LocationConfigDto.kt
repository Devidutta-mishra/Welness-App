package com.example.yourswelnes.feature.location.data.dto

import com.google.gson.annotations.SerializedName

data class LocationConfigDto(
    @SerializedName("trackingStartTime") val trackingStartTime: String? = null,
    @SerializedName("trackingEndTime") val trackingEndTime: String? = null,
    @SerializedName("trackingIntervalSeconds") val trackingIntervalSeconds: Int? = null,
    @SerializedName("uploadIntervalMinutes") val uploadIntervalMinutes: Int? = null,
    @SerializedName("dashboardUrl") val dashboardUrl: String? = null
)

package com.example.yourswelnes.feature.home.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * Flexible DTO to handle different response structures for user club details.
 */
data class ClubDetailsResponseDto(
    @SerializedName("status") val status: Any? = null,
    @SerializedName("success") val success: Any? = null,
    @SerializedName("message") val message: String? = null,
    
    // Top-level fields
    @SerializedName("id") val id: String? = null,
    @SerializedName("user_id") val userId: String? = null,
    @SerializedName("club_id") val clubId: String? = null,
    @SerializedName("club_name") val club_name: String? = null,
    @SerializedName("clubName") val clubName: String? = null,
    @SerializedName("groupName") val groupName: String? = null,
    @SerializedName("groupId") val groupId: String? = null,
    @SerializedName("cameraEnabled") val cameraEnabled: Boolean? = null,
    @SerializedName("trackingEnabled") val trackingEnabled: Boolean? = null,
    
    // Wrapped fields
    @SerializedName("user") val user: ClubDetailsDataDto? = null,
    @SerializedName("data") val data: ClubDetailsDataDto? = null
)

data class ClubDetailsDataDto(
    @SerializedName("id") val id: String? = null,
    @SerializedName("user_id") val userId: String? = null,
    @SerializedName("club_id") val clubId: String? = null,
    @SerializedName("club_name") val club_name: String? = null,
    @SerializedName("clubName") val clubName: String? = null,
    @SerializedName("groupName") val groupName: String? = null,
    @SerializedName("groupId") val groupId: String? = null,
    @SerializedName("cameraEnabled") val cameraEnabled: Boolean? = null,
    @SerializedName("trackingEnabled") val trackingEnabled: Boolean? = null
)

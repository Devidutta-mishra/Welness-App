package com.example.yourswelnes.feature.home.data.dto

import com.google.gson.annotations.SerializedName

data class GroupDetailsRequest(
    @SerializedName("userId") val userId: Int
)

data class GroupDetailsResponse(
    @SerializedName("success") val success: Boolean? = null,
    @SerializedName("error") val error: String? = null,
    @SerializedName("user_id") val userId: String? = null,
    @SerializedName("groups") val groups: List<GroupDto>? = null
)

data class GroupDto(
    @SerializedName("group_id") val groupId: Long,
    @SerializedName("group_name") val groupName: String,
    @SerializedName("messages") val messages: List<MessageDto>? = null
)

data class MessageDto(
    @SerializedName("keyword_id") val keywordId: Int,
    @SerializedName("keyword") val keyword: String,
    @SerializedName("point_value") val pointValue: Int,
    @SerializedName("start_time") val startTime: String,
    @SerializedName("end_time") val endTime: String
)

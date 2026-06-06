package com.example.yourswelnes.feature.monitoring.data.dto

import com.google.gson.annotations.SerializedName

data class AppDownloadListResponse(
    @SerializedName("success") val success: Boolean? = null,
    @SerializedName("app_downloads") val appDownloads: List<AppDownloadDto>? = null
)

data class AppDownloadDto(
    @SerializedName("id") val id: Int,
    @SerializedName("app_name") val appName: String,
    @SerializedName("download_link") val downloadLink: String
)

data class AppStatusUploadRequest(
    @SerializedName("userId") val userId: Int,
    @SerializedName("updatedApps") val updatedApps: List<UpdatedAppDto>
)

data class UpdatedAppDto(
    @SerializedName("appId") val appId: Int,
    @SerializedName("installStatus") val installStatus: Int
)

data class AppStatusUploadResponse(
    @SerializedName("success") val success: Boolean? = null
)

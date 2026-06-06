package com.example.yourswelnes.feature.monitoring.data.api

import com.example.yourswelnes.feature.monitoring.data.dto.AppDownloadListResponse
import com.example.yourswelnes.feature.monitoring.data.dto.AppStatusUploadRequest
import com.example.yourswelnes.feature.monitoring.data.dto.AppStatusUploadResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface AppMonitoringApi {

    @GET("api/app-download-list")
    suspend fun getAppDownloadList(): AppDownloadListResponse

    @POST("api/app-list-store")
    suspend fun storeAppStatuses(@Body request: AppStatusUploadRequest): AppStatusUploadResponse
}

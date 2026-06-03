package com.example.yourswelnes.feature.monitoring.data.remote.api

import com.example.yourswelnes.feature.monitoring.data.remote.dto.AppDownloadListResponse
import com.example.yourswelnes.feature.monitoring.data.remote.dto.AppStatusUploadRequest
import com.example.yourswelnes.feature.monitoring.data.remote.dto.AppStatusUploadResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface AppMonitoringApi {

    @GET("api/app-download-list")
    suspend fun getAppDownloadList(): AppDownloadListResponse

    @POST("api/app-list-store")
    suspend fun storeAppStatuses(@Body request: AppStatusUploadRequest): AppStatusUploadResponse
}

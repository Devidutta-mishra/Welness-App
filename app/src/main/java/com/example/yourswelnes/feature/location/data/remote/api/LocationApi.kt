package com.example.yourswelnes.feature.location.data.remote.api

import com.example.yourswelnes.feature.location.data.remote.dto.LocationConfigDto
import com.example.yourswelnes.feature.location.data.remote.dto.LocationUploadRequestDto
import com.example.yourswelnes.feature.location.data.remote.dto.LocationUploadResponseDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface LocationApi {

    @GET("api/location-tracking-time")
    suspend fun getLocationConfig(): LocationConfigDto

    @POST("api/store-location")
    suspend fun storeLocations(@Body request: LocationUploadRequestDto): LocationUploadResponseDto
}

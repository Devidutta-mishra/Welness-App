package com.example.yourswelnes.feature.location.data.api

import com.example.yourswelnes.feature.location.data.dto.LocationConfigDto
import com.example.yourswelnes.feature.location.data.dto.LocationUploadRequestDto
import com.example.yourswelnes.feature.location.data.dto.LocationUploadResponseDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface LocationApi {

    @GET("api/location-tracking-time")
    suspend fun getLocationConfig(): LocationConfigDto

    @POST("api/store-location")
    suspend fun storeLocations(@Body request: LocationUploadRequestDto): LocationUploadResponseDto
}

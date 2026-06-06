package com.example.yourswelnes.feature.home.data.api

import com.example.yourswelnes.feature.home.data.dto.GroupDetailsRequest
import com.example.yourswelnes.feature.home.data.dto.GroupDetailsResponse
import retrofit2.http.Body
import retrofit2.http.POST

interface GroupDetailsApi {

    @POST("api/group-details")
    suspend fun getGroupDetails(@Body request: GroupDetailsRequest): GroupDetailsResponse
}

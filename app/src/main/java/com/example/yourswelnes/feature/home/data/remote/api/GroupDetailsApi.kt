package com.example.yourswelnes.feature.home.data.remote.api

import com.example.yourswelnes.feature.home.data.remote.dto.GroupDetailsRequest
import com.example.yourswelnes.feature.home.data.remote.dto.GroupDetailsResponse
import retrofit2.http.Body
import retrofit2.http.POST

interface GroupDetailsApi {

    @POST("api/group-details")
    suspend fun getGroupDetails(@Body request: GroupDetailsRequest): GroupDetailsResponse
}

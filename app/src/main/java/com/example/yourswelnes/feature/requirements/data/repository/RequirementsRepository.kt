package com.example.yourswelnes.feature.requirements.data.repository

interface RequirementsRepository {
    fun isInternetAvailable(): Boolean
    fun isLocationEnabled(): Boolean
}

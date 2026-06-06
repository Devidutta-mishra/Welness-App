package com.example.yourswelnes.feature.onboarding.data

interface RequirementsRepository {
    fun isInternetAvailable(): Boolean
    fun isLocationEnabled(): Boolean
}

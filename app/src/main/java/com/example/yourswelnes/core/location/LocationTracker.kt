package com.example.yourswelnes.core.location

import android.location.Location

interface LocationTracker {
    suspend fun getCurrentLocation(): Location?
}

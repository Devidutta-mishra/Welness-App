package com.example.yourswelnes.feature.onboarding.data

import android.content.Context
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.core.location.LocationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RequirementsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : RequirementsRepository {

    override fun isInternetAvailable(): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    override fun isLocationEnabled(): Boolean {
        val lm = context.getSystemService(LocationManager::class.java)
        return LocationManagerCompat.isLocationEnabled(lm)
    }
}

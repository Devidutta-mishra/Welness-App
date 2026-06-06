package com.example.yourswelnes.core.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Priority
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber

@Singleton
class FusedLocationTracker @Inject constructor(
    private val fusedLocationClient: FusedLocationProviderClient,
    @ApplicationContext private val context: Context
) : LocationTracker {

    @SuppressLint("MissingPermission")
    override suspend fun getCurrentLocation(): Location? {
        if (!hasLocationPermission()) {
            Timber.w("PERMISSION DENIED | Location permission not granted — skipping GPS read")
            return null
        }
        return suspendCancellableCoroutine { cont ->
            fusedLocationClient
                .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener { location -> cont.resume(location) }
                .addOnFailureListener { e ->
                    Timber.e(e, "FusedLocationClient failed")
                    cont.resume(null)
                }
                .addOnCanceledListener { cont.cancel() }
        }
    }

    private fun hasLocationPermission(): Boolean =
        ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
        ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
}

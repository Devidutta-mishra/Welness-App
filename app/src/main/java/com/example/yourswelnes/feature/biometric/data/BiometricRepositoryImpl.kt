package com.example.yourswelnes.feature.biometric.data

import android.content.Context
import androidx.biometric.BiometricManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

@Singleton
class BiometricRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : BiometricRepository {

    override fun canAuthenticate(): Boolean {
        val manager = BiometricManager.from(context)
        val result = manager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
        )
        return when (result) {
            BiometricManager.BIOMETRIC_SUCCESS -> true
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                Timber.w("No biometric or device credential enrolled")
                false
            }
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> {
                Timber.w("No biometric hardware available")
                false
            }
            else -> {
                Timber.w("Biometric unavailable: result=$result")
                false
            }
        }
    }
}

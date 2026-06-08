package com.example.yourswelnes.feature.biometric.data

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.core.content.getSystemService
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

        // Prefer a strong biometric (e.g. fingerprint / face) when one is enrolled.
        val biometricStatus =
            manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        if (biometricStatus == BiometricManager.BIOMETRIC_SUCCESS) return true

        // No usable biometric — fall back to a device credential (PIN / pattern / password).
        // DEVICE_CREDENTIAL is only a queryable authenticator from API 30; below that we ask
        // the KeyguardManager whether a secure lock screen is set.
        val hasDeviceCredential = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            manager.canAuthenticate(BiometricManager.Authenticators.DEVICE_CREDENTIAL) ==
                    BiometricManager.BIOMETRIC_SUCCESS
        } else {
            context.getSystemService<KeyguardManager>()?.isDeviceSecure == true
        }

        if (!hasDeviceCredential) {
            Timber.w("No biometric or device credential available (biometric=$biometricStatus)")
        }
        return hasDeviceCredential
    }

    override fun isDeviceSecure(): Boolean =
        context.getSystemService<KeyguardManager>()?.isDeviceSecure == true
}

package com.example.yourswelnes.feature.biometric.security

import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import timber.log.Timber

class AuthenticationManager {

    fun showPrompt(
        activity: FragmentActivity,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)

        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                Timber.i("Biometric authentication succeeded")
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                // User cancellation is not an error — stay on lock screen silently
                if (errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                    errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON
                ) {
                    Timber.d("Biometric prompt dismissed by user")
                    return
                }
                Timber.w("Biometric error $errorCode: $errString")
                onError(errString.toString())
            }

            override fun onAuthenticationFailed() {
                Timber.w("Biometric authentication failed")
                onError("Authentication failed. Please try again.")
            }
        }

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Secure Access")
            .setSubtitle("Verify your identity to continue")
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    // Biometric, with automatic fall-back to PIN / pattern / password.
                    setAllowedAuthenticators(
                        BiometricManager.Authenticators.BIOMETRIC_STRONG or
                                BiometricManager.Authenticators.DEVICE_CREDENTIAL
                    )
                } else {
                    // API 29 (Android 10): BIOMETRIC_STRONG | DEVICE_CREDENTIAL is an
                    // unsupported combination for setAllowedAuthenticators and would crash
                    // build(). Use the legacy flag so users with no enrolled fingerprint are
                    // still prompted for their device credential.
                    @Suppress("DEPRECATION")
                    setDeviceCredentialAllowed(true)
                }
            }
            .build()

        BiometricPrompt(activity, executor, callback).authenticate(promptInfo)
    }
}

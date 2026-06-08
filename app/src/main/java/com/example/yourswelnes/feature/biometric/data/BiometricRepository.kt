package com.example.yourswelnes.feature.biometric.data

interface BiometricRepository {
    /** True when a biometric or device credential (PIN / pattern / password) is available to verify with. */
    fun canAuthenticate(): Boolean

    /** True when the device has a secure lock screen set (PIN / pattern / password, and therefore biometrics). */
    fun isDeviceSecure(): Boolean
}

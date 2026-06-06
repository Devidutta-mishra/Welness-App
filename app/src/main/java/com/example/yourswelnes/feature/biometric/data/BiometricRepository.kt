package com.example.yourswelnes.feature.biometric.data

interface BiometricRepository {
    fun canAuthenticate(): Boolean
}

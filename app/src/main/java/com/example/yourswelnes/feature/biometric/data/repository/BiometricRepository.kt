package com.example.yourswelnes.feature.biometric.data.repository

interface BiometricRepository {
    fun canAuthenticate(): Boolean
}

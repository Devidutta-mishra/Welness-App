package com.example.yourswelnes.feature.camera.presentation

import android.net.Uri

sealed class CameraUiState {
    object Idle : CameraUiState()
    object Capturing : CameraUiState()
    data class Captured(val photoUri: Uri) : CameraUiState()
    data class Error(val message: String) : CameraUiState()
}

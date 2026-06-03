package com.example.yourswelnes.navigation

object Destinations {
    const val SPLASH = "splash"
    const val LOGIN = "login"
    const val HOME = "home"
    const val CAMERA = "camera"

    private const val CAMERA_PREVIEW_BASE = "camera_preview"
    const val ARG_PHOTO_URI = "photoUri"
    const val CAMERA_PREVIEW = "$CAMERA_PREVIEW_BASE/{$ARG_PHOTO_URI}"

    /** [encodedUri] must already be URL-encoded via [android.net.Uri.encode]. */
    fun cameraPreview(encodedUri: String) = "$CAMERA_PREVIEW_BASE/$encodedUri"
}

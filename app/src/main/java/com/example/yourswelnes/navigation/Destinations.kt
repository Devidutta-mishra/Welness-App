package com.example.yourswelnes.navigation

object Destinations {
    const val SPLASH = "splash"
    const val REQUIREMENTS = "requirements/{nextDest}"
    const val WELCOME = "welcome"
    const val LOGIN = "login"
    const val BIOMETRIC_LOCK = "biometric_lock"
    const val HOME = "home"

    const val ARG_NEXT_DEST = "nextDest"
    fun requirements(nextDest: String) = "requirements/$nextDest"

    const val LOCATION_PERMISSION = "location_permission"
    const val NOTIFICATIONS = "notifications"

    const val ARG_GROUP_ID = "groupId"
    const val CAMERA = "camera/{$ARG_GROUP_ID}"
    fun camera(groupId: Long) = "camera/$groupId"

    private const val CAMERA_PREVIEW_BASE = "camera_preview"
    const val ARG_PHOTO_URI = "photoUri"
    const val CAMERA_PREVIEW = "$CAMERA_PREVIEW_BASE/{$ARG_PHOTO_URI}"

    /** [encodedUri] must already be URL-encoded via [android.net.Uri.encode]. */
    fun cameraPreview(encodedUri: String) = "$CAMERA_PREVIEW_BASE/$encodedUri"
}

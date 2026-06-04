package com.example.yourswelnes.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    // Brand
    primary          = PrimaryOrange,
    onPrimary        = Color.White,
    primaryContainer = PrimaryOrangeLight,
    onPrimaryContainer = PrimaryOrangeDark,

    // Surfaces
    background       = AppBackground,
    surface          = SurfaceWhite,
    surfaceVariant   = Color.White,

    // Text on surfaces
    onBackground     = TextPrimary,
    onSurface        = TextPrimary,
    onSurfaceVariant = TextSecondary,
    outline          = BorderLight,
    outlineVariant   = BorderMedium,

    // Content on colours
    onSecondary      = Color.White,
    onTertiary       = Color.White,

    // Error
    error            = ErrorRed,
    onError          = Color.White,
)

@Composable
fun YourswelnesTheme(
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        DisposableEffect(view) {
            val window = (view.context as Activity).window
            val insetsController = WindowCompat.getInsetsController(window, view)
            
            // Set default light theme appearance (dark icons)
            insetsController.isAppearanceLightStatusBars = true
            insetsController.isAppearanceLightNavigationBars = true
            
            onDispose {}
        }
    }

    MaterialTheme(
        colorScheme = LightColorScheme,
        typography  = Typography,
        content     = content
    )
}

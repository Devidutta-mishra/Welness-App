package com.example.yourswelnes.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    // Brand
    primary          = AppBlue,
    onPrimary        = Color.White,
    primaryContainer = AppBlueLight,
    onPrimaryContainer = AppBlueDark,

    // Surfaces
    background       = SurfaceWhite,
    surface          = SurfaceWhite,
    surfaceVariant   = Color(0xFFF5F5F5),

    // Text on surfaces — maximum contrast
    onBackground     = TextPrimary,
    onSurface        = TextPrimary,
    onSurfaceVariant = TextSecondary,
    outline          = TextTertiary,
    outlineVariant   = Color(0xFFDDDDDD),

    // Content on colours
    onSecondary      = Color.White,
    onTertiary       = Color.White,

    // Error
    error            = Color(0xFFD32F2F),
    onError          = Color.White,
)

@Composable
fun YourswelnesTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography  = Typography,
        content     = content
    )
}

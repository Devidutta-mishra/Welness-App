package com.example.yourswelnes.feature.location.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

@Composable
fun LocationPermissionScreen(
    onPermissionsGranted: () -> Unit,
    viewModel: LocationStatusViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Disable system back button to make permissions mandatory
    BackHandler(enabled = true) {
        // Do nothing, preventing back navigation
    }

    // Navigate away as soon as all permissions are confirmed
    LaunchedEffect(uiState.allPermissionsGranted) {
        if (uiState.allPermissionsGranted) onPermissionsGranted()
    }

    // Re-check all permissions and battery optimization state when the user returns from
    // the system settings dialogs (battery optimization, location settings, etc.).
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshPermissions()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Launcher for fine + coarse location
    val fineLocationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { viewModel.refreshPermissions() }

    // Launcher for background location (must be requested separately on API 29+)
    val backgroundLocationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { viewModel.refreshPermissions() }

    // Launcher for notification permission (API 33+)
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { viewModel.refreshPermissions() }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.96f else 1f, label = "scale")

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Location Access Required",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Step 1 — Fine location
            if (!uiState.hasFineLocationPermission) {
                Button(
                    onClick = {
                        fineLocationLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(47.dp)
                        .scale(scale),
                    shape = RoundedCornerShape(8.dp),
                    interactionSource = interactionSource
                ) {
                    Text(
                        text = "Grant Location Permission",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Step 2 — Background location (Android 10+, only after fine is granted)
            if (uiState.hasFineLocationPermission &&
                !uiState.hasBackgroundLocationPermission &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
            ) {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        backgroundLocationLauncher.launch(
                            Manifest.permission.ACCESS_BACKGROUND_LOCATION
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(47.dp)
                        .scale(scale),
                    shape = RoundedCornerShape(8.dp),
                    interactionSource = interactionSource
                ) {
                    Text(
                        text = "Allow Background Location",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Step 3 — Notifications (Android 13+)
            if (uiState.hasFineLocationPermission &&
                uiState.hasBackgroundLocationPermission &&
                !uiState.hasNotificationPermission &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
            ) {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(47.dp)
                        .scale(scale),
                    shape = RoundedCornerShape(8.dp),
                    interactionSource = interactionSource
                ) {
                    Text(
                        text = "Allow Notifications",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Step 4 — Battery optimization exemption.
            // Without this, many Android devices (Samsung, Xiaomi, Realme, OPPO) kill the
            // location service within seconds of the screen locking, stopping all GPS
            // collection. This is the most common cause of "stops tracking when phone is locked".
            if (uiState.hasFineLocationPermission &&
                uiState.hasBackgroundLocationPermission &&
                uiState.hasNotificationPermission &&
                !uiState.isBatteryOptimizationExempt
            ) {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                        intent.data = Uri.parse("package:${context.packageName}")
                        context.startActivity(intent)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(47.dp)
                        .scale(scale),
                    shape = RoundedCornerShape(8.dp),
                    interactionSource = interactionSource
                ) {
                    Text(
                        text = "Disable Battery Optimization",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// ── Reusable status components ────────────────────────────────────────────────

@Composable
fun LocationStatusComponent(uiState: LocationUiState, modifier: Modifier = Modifier) {
    val statusText = when {
        !uiState.hasFineLocationPermission -> "Location permission required"
        !uiState.hasBackgroundLocationPermission -> "Background location required"
        uiState.isServiceRunning && uiState.isInTrackingWindow -> "Tracking active"
        uiState.isServiceRunning && !uiState.isInTrackingWindow -> "Service running — outside window"
        else -> "Tracking inactive"
    }

    Text(
        text = statusText,
        style = MaterialTheme.typography.bodySmall,
        color = if (uiState.isServiceRunning && uiState.isInTrackingWindow)
            MaterialTheme.colorScheme.primary
        else
            MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
    )
}

@Composable
fun TrackingStatusComponent(uiState: LocationUiState, modifier: Modifier = Modifier) {
    val syncText = uiState.lastSyncTime?.let { ts ->
        val diff = (System.currentTimeMillis() - ts) / 60_000L
        "Last upload: ${diff}m ago"
    } ?: "No uploads yet"

    Text(
        text = syncText,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
    )
}

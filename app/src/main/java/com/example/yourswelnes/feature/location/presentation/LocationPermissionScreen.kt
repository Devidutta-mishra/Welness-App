package com.example.yourswelnes.feature.location.presentation

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun LocationPermissionScreen(
    onPermissionsGranted: () -> Unit,
    viewModel: LocationStatusViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // Disable system back button to make permissions mandatory
    BackHandler(enabled = true) {
        // Do nothing, preventing back navigation
    }

    // Navigate away as soon as all permissions are confirmed
    LaunchedEffect(uiState.allPermissionsGranted) {
        if (uiState.allPermissionsGranted) onPermissionsGranted()
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
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "This app tracks your location between 6:00 AM and 12:00 PM to record attendance. " +
                    "Location is collected in the background even when the app is closed.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
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
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Grant Location Permission")
                }
            }

            // Step 2 — Background location (Android 10+, only after fine is granted)
            if (uiState.hasFineLocationPermission &&
                !uiState.hasBackgroundLocationPermission &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
            ) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Next: allow location access 'All the time' in the next prompt so tracking continues when the app is closed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        backgroundLocationLauncher.launch(
                            Manifest.permission.ACCESS_BACKGROUND_LOCATION
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Allow Background Location")
                }
            }

            // Step 3 — Notifications (Android 13+)
            if (uiState.hasFineLocationPermission &&
                uiState.hasBackgroundLocationPermission &&
                !uiState.hasNotificationPermission &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
            ) {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Allow Notifications")
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

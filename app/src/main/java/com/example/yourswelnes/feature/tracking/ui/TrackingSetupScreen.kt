package com.example.yourswelnes.feature.tracking.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.yourswelnes.core.tracking.OemSetupStep
import com.example.yourswelnes.core.ui.theme.ErrorRed
import com.example.yourswelnes.core.ui.theme.SuccessGreen
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val TIMESTAMP_FMT = DateTimeFormatter.ofPattern("d MMM, HH:mm")

/**
 * Comprehensive background tracking setup screen.
 *
 * When [blockBackNavigation] is true (used as the mandatory permission gate), the back
 * button is disabled and the screen auto-exits via [onDone] when all permissions are met.
 *
 * When [blockBackNavigation] is false (accessed from the home health card), the back
 * button is enabled and [onDone] is shown as an explicit "Done" button.
 */
@Composable
fun TrackingSetupScreen(
    onDone: () -> Unit,
    blockBackNavigation: Boolean = true,
    viewModel: TrackingSetupViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    if (blockBackNavigation) {
        BackHandler(enabled = true) { /* mandatory — no back */ }
    }

    // Auto-exit (blocking mode only) once mandatory permissions are satisfied.
    // Battery optimization is recommended but must NOT block exit — Android cannot reliably
    // verify that the user exempted the app on all OEM devices.
    LaunchedEffect(uiState.mandatoryPermissionsGranted) {
        if (blockBackNavigation && uiState.mandatoryPermissionsGranted) onDone()
    }

    // Recheck permissions every time the user returns from a system settings screen.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshStatus()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Permission launchers
    val fineLocationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { viewModel.refreshStatus() }

    val backgroundLocationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { viewModel.refreshStatus() }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { viewModel.refreshStatus() }

    // Must use StartActivityForResult, not Context.startActivity — see PermissionWizardScreen
    // for full explanation of why FLAG_ACTIVITY_NEW_TASK breaks the onResume lifecycle.
    val batterySettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { viewModel.refreshStatus() }

    if (!uiState.hasChecked) {
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!blockBackNavigation) {
                IconButton(onClick = onDone) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back"
                    )
                }
            } else {
                Spacer(modifier = Modifier.width(48.dp))
            }
            Text(
                text = "Background Tracking Setup",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.width(48.dp))
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "This app automatically records club entry and exit locations even when the phone is locked. " +
                    "Some devices require additional settings for reliable background tracking.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Start
            )

            Spacer(modifier = Modifier.height(24.dp))

            // ── Auto-verified permissions ────────────────────────────────────
            SectionHeader(title = "Required Permissions")
            Spacer(modifier = Modifier.height(8.dp))

            PermissionRow(
                label = "Location",
                description = "Required for GPS tracking.",
                isGranted = uiState.hasFineLocationPermission,
                actionLabel = "Grant Permission",
                onAction = {
                    fineLocationLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            PermissionRow(
                label = "Background Location",
                description = "Required for tracking when app is not open.",
                isGranted = uiState.hasBackgroundLocationPermission,
                actionEnabled = uiState.hasFineLocationPermission,
                actionLabel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                    "Allow Always" else "Grant",
                onAction = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        backgroundLocationLauncher.launch(
                            Manifest.permission.ACCESS_BACKGROUND_LOCATION
                        )
                    }
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            PermissionRow(
                label = "Notifications",
                description = "Required for tracking status and GPS alerts.",
                isGranted = uiState.hasNotificationPermission,
                actionLabel = "Grant Permission",
                onAction = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            PermissionRow(
                label = "Battery Optimization",
                description = "Recommended: prevents your device from killing background tracking when locked.",
                isGranted = uiState.isBatteryOptimizationExempt,
                grantedLabel = "Exempted",
                missingLabel = "Recommended — tap to open",
                actionLabel = "Open Battery Settings",
                isRecommended = true,
                onAction = {
                    val packageUri = Uri.parse("package:${context.packageName}")
                    try {
                        batterySettingsLauncher.launch(
                            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri)
                        )
                    } catch (e: ActivityNotFoundException) {
                        try {
                            batterySettingsLauncher.launch(
                                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                            )
                        } catch (e2: ActivityNotFoundException) {
                            batterySettingsLauncher.launch(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri)
                            )
                        }
                    }
                }
            )

            // ── OEM-specific guidance ────────────────────────────────────────
            if (uiState.hasOemSteps) {
                Spacer(modifier = Modifier.height(24.dp))
                SectionHeader(title = "${uiState.oemProfile.displayName} Setup")
                Spacer(modifier = Modifier.height(8.dp))

                uiState.oemProfile.steps.forEachIndexed { index, step ->
                    OemStepRow(
                        stepNumber = index + 1,
                        step = step,
                        onAction = { step.launchAction(context) }
                    )
                    if (index < uiState.oemProfile.steps.lastIndex) {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }

            // ── Tracking health summary ──────────────────────────────────────
            Spacer(modifier = Modifier.height(24.dp))
            SectionHeader(title = "Tracking Status")
            Spacer(modifier = Modifier.height(8.dp))

            TrackingHealthSummary(uiState = uiState)

            Spacer(modifier = Modifier.height(24.dp))
        }

        // Continue button:
        //   Blocking mode  — visible once mandatory permissions are granted (3 Android permissions).
        //   Non-blocking   — always visible so the user can exit at any time.
        if (!blockBackNavigation || uiState.mandatoryPermissionsGranted) {
            Button(
                onClick = onDone,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = if (blockBackNavigation) "Continue" else "Done",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ── Components ────────────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
    )
    Spacer(modifier = Modifier.height(4.dp))
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun PermissionRow(
    label: String,
    description: String,
    isGranted: Boolean,
    grantedLabel: String = "Granted",
    missingLabel: String = "Missing",
    actionLabel: String,
    actionEnabled: Boolean = true,
    // Recommended rows use an amber warning icon instead of red — they don't block progress.
    isRecommended: Boolean = false,
    onAction: () -> Unit
) {
    val missingTint = if (isRecommended) Color(0xFFF59E0B) else ErrorRed

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = Color.White,
        shadowElevation = 2.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (isRecommended && !isGranted) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Color(0xFFFFF8E1),
                            shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
                        )
                        .padding(horizontal = 16.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = "Recommended",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFD97706)
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (isGranted) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                        contentDescription = null,
                        tint = if (isGranted) SuccessGreen else missingTint,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isGranted) grantedLabel else if (isRecommended) missingLabel else description,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isGranted) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (!isGranted) {
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = onAction,
                        enabled = actionEnabled,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 12.dp, vertical = 6.dp
                        )
                    ) {
                        Text(
                            text = actionLabel,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OemStepRow(
    stepNumber: Int,
    step: OemSetupStep,
    onAction: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = Color.White,
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(24.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "$stepNumber",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = step.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = step.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 34.dp)
            )
            if (!step.isManual) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onAction,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.padding(start = 34.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 12.dp, vertical = 6.dp
                    )
                ) {
                    Text(
                        text = step.actionLabel,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            } else {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.padding(start = 34.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Manual step — follow the instructions above",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun TrackingHealthSummary(uiState: TrackingSetupUiState) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = Color.White,
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            HealthRow("Last Location Collected", uiState.lastLocationCollectionTime)
            HealthRow("Last Upload",             uiState.lastUploadTime)
            HealthRow("Last Config Sync",        uiState.lastTimingSyncTime)
            HealthRow("Last Watchdog Run",       uiState.lastWorkerExecutionTime)
        }
    }
}

@Composable
private fun HealthRow(label: String, timestampMs: Long?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = timestampMs?.let { ts ->
                val diffMin = (System.currentTimeMillis() - ts) / 60_000L
                when {
                    diffMin < 1    -> "Just now"
                    diffMin < 60   -> "${diffMin}m ago"
                    diffMin < 1440 -> "${diffMin / 60}h ago"
                    else           -> LocalDateTime.ofInstant(
                        Instant.ofEpochMilli(ts), ZoneId.systemDefault()
                    ).format(TIMESTAMP_FMT)
                }
            } ?: "Never",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = if (timestampMs != null)
                MaterialTheme.colorScheme.onSurface
            else
                MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

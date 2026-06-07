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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.SettingsApplications
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

@Composable
fun PermissionWizardScreen(
    onDone: () -> Unit,
    viewModel: PermissionWizardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Wizard is a mandatory gate — back navigation is disabled.
    BackHandler(enabled = true) { /* must complete the wizard */ }

    LaunchedEffect(viewModel) {
        viewModel.done.collect { onDone() }
    }

    // Re-check permissions on every Activity resume — covers returning from:
    //   • Android permission dialogs
    //   • System Settings (any screen)
    //   • OEM battery/auto-start settings
    // For the battery step, the ViewModel introduces a 400ms delay before querying
    // PowerManager, which eliminates the false-negative window on Xiaomi/Vivo/Oppo.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.onResumeRefresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // ── Permission launchers ───────────────────────────────────────────────────
    // Each permission type gets its own launcher. Android requires separate instances;
    // reusing a launcher for different permissions produces incorrect results.

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { viewModel.onResumeRefresh() }

    val fineLocationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { viewModel.onResumeRefresh() }

    val backgroundLocationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { viewModel.onResumeRefresh() }

    // Battery optimization MUST use StartActivityForResult, never Context.startActivity.
    //
    // Context.startActivity with FLAG_ACTIVITY_NEW_TASK brings the Settings task to the
    // foreground without calling Activity.onPause() on the calling app when the Settings
    // task was already running. No onPause() → no onResume() → the DisposableEffect
    // observer never fires → isIgnoringBatteryOptimizations() is never re-evaluated.
    //
    // StartActivityForResult always calls onPause() and delivers a result callback regardless
    // of task state, which guarantees the re-check always happens.
    val batterySettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { viewModel.onResumeRefresh() }

    if (uiState.isLoading || uiState.steps.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        )
        return
    }

    val step = uiState.currentStep ?: return

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        StepProgressBar(
            currentIndex = uiState.currentStepIndex,
            total = uiState.totalSteps
        )

        AnimatedContent(
            targetState = uiState.currentStepIndex,
            transitionSpec = {
                (slideInHorizontally { it } + fadeIn()).togetherWith(
                    slideOutHorizontally { -it } + fadeOut()
                )
            },
            label = "wizard_step",
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) { index ->
            val displayStep = uiState.steps.getOrNull(index) ?: return@AnimatedContent
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                WizardStepContent(step = displayStep)
            }
        }

        WizardActionButtons(
            step = step,
            onPrimaryAction = {
                when (step.type) {
                    WizardStepType.FINE_LOCATION ->
                        fineLocationLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )

                    WizardStepType.BACKGROUND_LOCATION -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            backgroundLocationLauncher.launch(
                                Manifest.permission.ACCESS_BACKGROUND_LOCATION
                            )
                        } else {
                            viewModel.onResumeRefresh()
                        }
                    }

                    WizardStepType.NOTIFICATION -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            viewModel.onResumeRefresh()
                        }
                    }

                    WizardStepType.OEM_STEP -> {
                        val oemStep = step.oemStep
                        if (oemStep != null && !oemStep.isManual) {
                            // Launch the OEM settings screen then mark the step dismissed.
                            // We call advance() immediately after launch because the OEM screen
                            // has no verifiable completion state — recording the intent launch
                            // itself is the best signal we have that the user engaged with it.
                            oemStep.launchAction(context)
                            viewModel.advance()
                        } else {
                            // Manual step (e.g. "Lock in Recents") — primary button is "I've Done This".
                            viewModel.advance()
                        }
                    }

                    WizardStepType.BATTERY_OPTIMIZATION -> {
                        val packageUri = Uri.parse("package:${context.packageName}")
                        try {
                            batterySettingsLauncher.launch(
                                Intent(
                                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                    packageUri
                                )
                            )
                        } catch (e: ActivityNotFoundException) {
                            // ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS throws on some Xiaomi
                            // builds that restrict per-app battery settings via their own UI.
                            try {
                                batterySettingsLauncher.launch(
                                    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                )
                            } catch (e2: ActivityNotFoundException) {
                                batterySettingsLauncher.launch(
                                    Intent(
                                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        packageUri
                                    )
                                )
                            }
                        }
                    }
                }
            },
            onSkip = viewModel::advance
        )
    }
}

// ── Internal composables ──────────────────────────────────────────────────────

@Composable
private fun StepProgressBar(currentIndex: Int, total: Int) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(top = 16.dp, bottom = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            repeat(total) { index ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp)
                        .background(
                            color = if (index <= currentIndex) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(2.dp)
                        )
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Step ${currentIndex + 1} of $total",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun WizardStepContent(step: WizardStep) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp)
            .padding(top = 32.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(88.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = iconForStep(step.type),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(44.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        if (!step.isMandatory) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFFFFF8E1)
            ) {
                Text(
                    text = "Recommended",
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFD97706)
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
        }

        Text(
            text = step.title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = step.bodyText,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        if (step.noteText != null) {
            Spacer(modifier = Modifier.height(20.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFFFF3E0)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = null,
                        tint = Color(0xFFE65100),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = step.noteText,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF5D4037)
                    )
                }
            }
        }
    }
}

@Composable
private fun WizardActionButtons(
    step: WizardStep,
    onPrimaryAction: () -> Unit,
    onSkip: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val isManualOemStep = step.type == WizardStepType.OEM_STEP && step.oemStep?.isManual == true

        Button(
            onClick = onPrimaryAction,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Icon(
                imageVector = if (isManualOemStep) Icons.Filled.CheckCircle
                else iconForStep(step.type),
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = step.primaryButtonLabel,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }

        if (!step.isMandatory) {
            TextButton(
                onClick = onSkip,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Skip for Now",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun iconForStep(type: WizardStepType): ImageVector = when (type) {
    WizardStepType.FINE_LOCATION        -> Icons.Filled.LocationOn
    WizardStepType.BACKGROUND_LOCATION  -> Icons.Filled.MyLocation
    WizardStepType.NOTIFICATION         -> Icons.Filled.Notifications
    WizardStepType.BATTERY_OPTIMIZATION -> Icons.Filled.BatteryChargingFull
    WizardStepType.OEM_STEP             -> Icons.Filled.SettingsApplications
}

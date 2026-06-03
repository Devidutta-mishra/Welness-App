package com.example.yourswelnes.feature.requirements.presentation

import android.content.Intent
import android.provider.Settings
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.SignalCellularConnectedNoInternet0Bar
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
fun RequirementsScreen(
    nextDestination: String,
    onRequirementsMet: (String) -> Unit,
    viewModel: RequirementsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Collect navigation event and forward to caller
    LaunchedEffect(viewModel) {
        viewModel.navigationEvent.collect { dest ->
            onRequirementsMet(dest)
        }
    }

    // Recheck every time screen becomes visible (including return from Settings)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.checkAndProceed(nextDestination)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // While the first check hasn't run yet, show a blank white screen to avoid
    // flashing the blocking UI on devices where all requirements are already met.
    if (!uiState.hasChecked) {
        Box(modifier = androidx.compose.ui.Modifier.fillMaxSize().background(Color.White))
        return
    }

    // Both requirements satisfied — navigation event already emitted, nothing to render.
    if (uiState.allMet) return

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Requirements Needed",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "To continue using Yours Wellness,\nplease enable the following:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(36.dp))

        // Only show rows for what's actually missing (or confirmed present)
        RequirementRow(
            label = "Internet Connection",
            isMet = uiState.isInternetAvailable,
            metIcon = Icons.Filled.Wifi,
            notMetIcon = Icons.Filled.SignalCellularConnectedNoInternet0Bar
        )

        Spacer(modifier = Modifier.height(16.dp))

        RequirementRow(
            label = "Location Services",
            isMet = uiState.isLocationEnabled,
            metIcon = Icons.Filled.LocationOn,
            notMetIcon = Icons.Filled.LocationOff
        )

        Spacer(modifier = Modifier.height(40.dp))

        // Show targeted settings button based on what's missing
        if (!uiState.isInternetAvailable) {
            SettingsButton(
                label = "Open Internet Settings",
                onClick = {
                    context.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    })
                }
            )
        }

        if (!uiState.isLocationEnabled) {
            if (!uiState.isInternetAvailable) Spacer(modifier = Modifier.height(12.dp))
            SettingsButton(
                label = "Open Location Settings",
                onClick = {
                    context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    })
                }
            )
        }
    }
}

@Composable
private fun RequirementRow(
    label: String,
    isMet: Boolean,
    metIcon: ImageVector,
    notMetIcon: ImageVector
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = if (isMet) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Icon(
                    imageVector = if (isMet) metIcon else notMetIcon,
                    contentDescription = null,
                    tint = if (isMet) Color(0xFF2E7D32) else Color(0xFFC62828),
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = if (isMet) Color(0xFF2E7D32) else Color(0xFFC62828)
                )
            }
            Icon(
                imageVector = if (isMet) Icons.Filled.CheckCircle else notMetIcon,
                contentDescription = null,
                tint = if (isMet) Color(0xFF2E7D32) else Color(0xFFC62828),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun SettingsButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF2F80ED),
            contentColor = Color.White
        )
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
    }
}

package com.example.yourswelnes.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    onCameraClick: () -> Unit = {},
    onNotificationsClick: () -> Unit = {},
    onLogoutClick: () -> Unit = {},
    onDashboardClick: () -> Unit = {},
    onFabClick: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()
    HomeScreenContent(
        uiState = state,
        onCameraClick = onCameraClick,
        onNotificationsClick = onNotificationsClick,
        onLogoutClick = onLogoutClick,
        onLogoutConfirmed = viewModel::onLogoutConfirmed,
        onLogoutDismissed = viewModel::onLogoutDismissed,
        onDashboardClick = onDashboardClick,
        onFabClick = onFabClick
    )
}

@Composable
fun HomeScreenContent(
    uiState: HomeUiState,
    onCameraClick: () -> Unit = {},
    onNotificationsClick: () -> Unit = {},
    onLogoutClick: () -> Unit = {},
    onLogoutConfirmed: () -> Unit = {},
    onLogoutDismissed: () -> Unit = {},
    onDashboardClick: () -> Unit = {},
    onFabClick: () -> Unit = {}
) {
    if (uiState.showLogoutDialog) {
        LogoutDialog(onConfirm = onLogoutConfirmed, onDismiss = onLogoutDismissed)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFFFFF6F8), Color.White)
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(20.dp))
            HomeTopBar(
                modifier = Modifier.fillMaxWidth(),
                onCameraClick = onCameraClick,
                onNotificationsClick = onNotificationsClick,
                onLogoutClick = onLogoutClick
            )

            Spacer(modifier = Modifier.height(80.dp))

            ProfileHeader(
                userName = uiState.userName,
                clubName = uiState.clubName,
                isLoadingClub = uiState.isLoading
            )

            Spacer(modifier = Modifier.height(28.dp))

            DashboardCard(
                isLoading = uiState.isDashboardLoading,
                errorMessage = uiState.dashboardError,
                onClick = onDashboardClick
            )

            Spacer(modifier = Modifier.weight(1f))
        }

        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(90.dp),
            color = Color.White,
            shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
            tonalElevation = 2.dp
        ) {}

        BottomSquareButton(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = (-30).dp),
            onClick = onFabClick
        )
    }
}

@Composable
private fun DashboardCard(
    isLoading: Boolean,
    errorMessage: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(4.dp, shape = RoundedCornerShape(16.dp))
                .clickable(enabled = !isLoading, onClick = onClick),
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF2F80ED),
            tonalElevation = 4.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "My Dashboard",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Open web portal",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.80f)
                    )
                }

                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.5.dp,
                        color = Color.White
                    )
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        if (!errorMessage.isNullOrBlank()) {
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 6.dp, start = 4.dp)
            )
        }
    }
}

@Composable
private fun HomeTopBar(
    modifier: Modifier = Modifier,
    onCameraClick: () -> Unit,
    onNotificationsClick: () -> Unit,
    onLogoutClick: () -> Unit
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(36.dp),
            shape = CircleShape,
            color = Color(0xFF2F80ED)
        ) {
            Icon(
                imageVector = Icons.Filled.FitnessCenter,
                contentDescription = "yourswelnes",
                tint = Color.White,
                modifier = Modifier.padding(8.dp)
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onCameraClick) {
                Icon(
                    imageVector = Icons.Filled.CameraAlt,
                    contentDescription = "Camera",
                    tint = Color(0xFF222222)
                )
            }
            IconButton(onClick = onNotificationsClick) {
                Icon(
                    imageVector = Icons.Filled.Notifications,
                    contentDescription = "Notifications",
                    tint = Color(0xFF222222)
                )
            }
            IconButton(onClick = onLogoutClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Logout,
                    contentDescription = "Logout",
                    tint = Color(0xFF222222)
                )
            }
        }
    }
}

@Composable
private fun ProfileHeader(
    userName: String,
    clubName: String,
    isLoadingClub: Boolean
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            shape = CircleShape,
            color = Color(0xFFFFE8EF),
            tonalElevation = 6.dp,
            shadowElevation = 8.dp,
            modifier = Modifier.size(140.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Person,
                contentDescription = "Profile",
                tint = Color(0xFFE56B8C),
                modifier = Modifier.padding(34.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = userName,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF222222)
        )

        Spacer(modifier = Modifier.height(4.dp))

        if (isLoadingClub) {
            Text(
                text = "Loading club details...",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray
            )
        } else if (clubName.isNotEmpty()) {
            Text(
                text = clubName,
                style = MaterialTheme.typography.bodyLarge,
                color = Color(0xFF666666)
            )
        }
    }
}

@Composable
private fun BottomSquareButton(modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .size(68.dp)
            .shadow(8.dp, shape = RoundedCornerShape(14.dp))
            .background(color = Color(0xFF2F80ED), shape = RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.CameraAlt,
            contentDescription = "Main action",
            tint = Color.White,
            modifier = Modifier.size(32.dp)
        )
    }
}

@Composable
private fun LogoutDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Logout") },
        text = { Text(text = "Are you sure you want to log out?") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = "Logout", color = Color(0xFFE56B8C))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(text = "Cancel") }
        }
    )
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    HomeScreenContent(
        uiState = HomeUiState(userName = "Ansuman Senapati", clubName = "Young Women's Club"),
        onDashboardClick = {}
    )
}

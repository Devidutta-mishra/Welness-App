package com.example.yourswelnes.feature.home.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.yourswelnes.R
import com.example.yourswelnes.feature.location.presentation.LocationStatusViewModel

@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    locationViewModel: LocationStatusViewModel = hiltViewModel(),
    groupScheduleViewModel: GroupScheduleViewModel = hiltViewModel(),
    onNotificationsClick: () -> Unit = {},
    onLogoutClick: () -> Unit = {},
    onDashboardClick: () -> Unit = {},
    onFabClick: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        locationViewModel.refreshPermissions()
    }

    HomeScreenContent(
        uiState = state,
        groupScheduleViewModel = groupScheduleViewModel,
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
    groupScheduleViewModel: GroupScheduleViewModel? = null,
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
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            HomeTopBar(
                modifier = Modifier.fillMaxWidth(),
                hasUnread = uiState.hasUnreadNotifications,
                onNotificationsClick = onNotificationsClick,
                onLogoutClick = onLogoutClick
            )

            Spacer(modifier = Modifier.height(70.dp))

            ProfileHeader(
                userName = uiState.userName,
                clubName = uiState.clubName,
                isLoadingClub = uiState.isLoading,
                profileImageUrl = uiState.profileImageUrl
            )

            Spacer(modifier = Modifier.height(30.dp))

            DashboardCard(
                isLoading = uiState.isDashboardLoading,
                errorMessage = uiState.dashboardError,
                onClick = onDashboardClick
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (groupScheduleViewModel != null) {
                GroupScheduleSection(viewModel = groupScheduleViewModel)
            }

            Spacer(modifier = Modifier.weight(1f))
        }

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
            color = Color.White,
            border = BorderStroke(1.dp, Color.Black.copy(alpha = 0.5f)),
            shadowElevation = 0.dp,
            tonalElevation = 0.dp
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
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Open web portal",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.5.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
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
    hasUnread: Boolean,
    onNotificationsClick: () -> Unit,
    onLogoutClick: () -> Unit
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = Color.White,
                shadowElevation = 2.dp
            ) {
                Image(
                    painter = painterResource(id = R.drawable.app_logo),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Yours Wellness Center",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onNotificationsClick) {
                BadgedBox(
                    badge = {
                        if (hasUnread) {
                            Badge(
                                modifier = Modifier.size(8.dp),
                                containerColor = Color.Red
                            )
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Filled.Notifications,
                        contentDescription = "Notifications",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            IconButton(onClick = onLogoutClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Logout,
                    contentDescription = "Logout",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun ProfileHeader(
    userName: String,
    clubName: String,
    isLoadingClub: Boolean,
    profileImageUrl: String? = null
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            shape = CircleShape,
            color = Color(0xFFE3F2FD),
            tonalElevation = 16.dp,
            shadowElevation = 15.dp,
            modifier = Modifier.size(140.dp)
        ) {
            if (!profileImageUrl.isNullOrBlank()) {
                AsyncImage(
                    model = profileImageUrl,
                    contentDescription = "Profile",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    placeholder = rememberVectorPainter(Icons.Filled.Person),
                    error = rememberVectorPainter(Icons.Filled.Person)
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = "Profile",
                    tint = Color(0xFF2F80ED),
                    modifier = Modifier.padding(34.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = userName,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(4.dp))

        if (isLoadingClub) {
            Text(
                text = "Loading club details...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else if (clubName.isNotEmpty()) {
            Text(
                text = clubName,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
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
                Text(text = "Logout", color = Color.Red, fontWeight = FontWeight.Bold)
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

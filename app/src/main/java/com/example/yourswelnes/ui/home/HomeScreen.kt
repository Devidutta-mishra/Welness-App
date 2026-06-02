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
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Home screen UI implementation (Phase 1) — UI only, no location or camera behaviour.
 * Produces a layout that matches the provided design: top icons, centered profile
 * image and name, and a rounded square action button at the bottom center.
 */
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = viewModel(),
    onCameraClick: () -> Unit = {},
    onNotificationsClick: () -> Unit = {},
    onLogoutClick: () -> Unit = {},
    onFabClick: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()
    HomeScreenContent(
        uiState = state,
        onCameraClick = onCameraClick,
        onNotificationsClick = onNotificationsClick,
        onLogoutClick = onLogoutClick,
        onFabClick = onFabClick
    )
}


@Composable
fun HomeScreenContent(
    uiState: HomeUiState,
    onCameraClick: () -> Unit = {},
    onNotificationsClick: () -> Unit = {},
    onLogoutClick: () -> Unit = {},
    onFabClick: () -> Unit = {}
) {
    // Background gradient similar to the reference design (soft pink -> white)
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

            ProfileHeader(userName = uiState.userName)

            // flexible empty space to push FAB toward bottom like the design
            Spacer(modifier = Modifier.weight(1f))
        }

        // Bottom white bar to match the design's lower sheet
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(90.dp),
            color = Color.White,
            shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
            tonalElevation = 2.dp
        ) {
            // intentionally empty - acts as background for the FAB
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
private fun ProfileHeader(userName: String) {
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

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    HomeScreenContent(
        uiState = HomeUiState(userName = "Ansuman Senapati"),
        onCameraClick = {},
        onNotificationsClick = {},
        onLogoutClick = {},
        onFabClick = {}
    )
}

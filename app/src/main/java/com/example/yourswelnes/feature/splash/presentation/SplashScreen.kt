package com.example.yourswelnes.feature.splash.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.yourswelnes.ui.theme.YourswelnesTheme

@Composable
fun SplashScreen(
    uiState: SplashUiState,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFFFF4F7),
                        Color(0xFFF8FBFF),
                        Color.White
                    )
                )
            )
            .systemBarsPadding()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                modifier = Modifier.size(112.dp),
                shape = CircleShape,
                color = Color(0xFF2F80ED),
                shadowElevation = 12.dp
            ) {
                Icon(
                    imageVector = Icons.Filled.FitnessCenter,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.padding(28.dp)
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = "yourswelnes",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF172033)
            )
            Text(
                text = "Fitness tracking made simple",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF667085),
                modifier = Modifier.padding(top = 8.dp)
            )

            Spacer(modifier = Modifier.height(36.dp))

            if (uiState.isLoading) {
                CircularProgressIndicator(
                    color = Color(0xFF2F80ED),
                    modifier = Modifier.size(32.dp),
                    strokeWidth = 3.dp
                )
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun SplashScreenPreview() {
    YourswelnesTheme {
        SplashScreen(uiState = SplashUiState())
    }
}

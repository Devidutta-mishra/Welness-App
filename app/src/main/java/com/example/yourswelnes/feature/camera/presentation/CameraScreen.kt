package com.example.yourswelnes.feature.camera.presentation

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import timber.log.Timber

@Composable
fun CameraScreen(
    onBack: () -> Unit,
    onPhotoCaptured: (String) -> Unit,
    viewModel: CameraViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val view = LocalView.current
    val uiState by viewModel.uiState.collectAsState()
    val lensFacing by viewModel.lensFacing.collectAsState()
    val flashMode by viewModel.flashMode.collectAsState()

    // Ensure status bar icons are light for the dark camera background
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
        }
    }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    LaunchedEffect(uiState) {
        val state = uiState
        if (state is CameraUiState.Captured) {
            val encoded = Uri.encode(state.photoUri.toString())
            viewModel.retake()
            onPhotoCaptured(encoded)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (hasCameraPermission) {
            LiveCameraPreview(
                lensFacing = lensFacing,
                flashMode = flashMode,
                isCapturing = uiState is CameraUiState.Capturing,
                onCaptureRequest = { imageCapture -> viewModel.capturePhoto(imageCapture) },
                onToggleFlash = viewModel::toggleFlashMode,
                onToggleLens = viewModel::toggleLens,
                onBack = onBack,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            CameraPermissionDeniedContent(
                onBack = onBack,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun LiveCameraPreview(
    lensFacing: Int,
    flashMode: Int,
    isCapturing: Boolean,
    onCaptureRequest: (ImageCapture) -> Unit,
    onToggleFlash: () -> Unit,
    onToggleLens: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    val preview = remember { Preview.Builder().build() }
    val imageCapture = remember { ImageCapture.Builder().build() }

    LaunchedEffect(flashMode) {
        imageCapture.flashMode = flashMode
    }

    val cameraSelector = remember(lensFacing) {
        CameraSelector.Builder().requireLensFacing(lensFacing).build()
    }

    DisposableEffect(lensFacing) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        var boundProvider: ProcessCameraProvider? = null

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            boundProvider = cameraProvider
            cameraProvider.unbindAll()
            try {
                cameraProvider.bindToLifecycle(
                    lifecycleOwner, cameraSelector, preview, imageCapture
                )
                preview.setSurfaceProvider(previewView.surfaceProvider)
            } catch (e: Exception) {
                Timber.e(e, "CameraX binding failed")
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose { boundProvider?.unbindAll() }
    }

    Box(modifier = modifier) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )

        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 4.dp)
                .align(Alignment.TopStart),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }
            Box(modifier = Modifier.weight(1f))
            IconButton(onClick = onToggleFlash) {
                Icon(
                    imageVector = flashIconForMode(flashMode),
                    contentDescription = flashContentDescription(flashMode),
                    tint = Color.White
                )
            }
            IconButton(onClick = onToggleLens) {
                Icon(
                    imageVector = Icons.Filled.Cameraswitch,
                    contentDescription = "Switch camera",
                    tint = Color.White
                )
            }
        }

        // Shutter button
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 40.dp)
        ) {
            if (isCapturing) {
                CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.size(72.dp)
                )
            } else {
                ShutterButton(onClick = { onCaptureRequest(imageCapture) })
            }
        }
    }
}

private fun flashIconForMode(flashMode: Int): ImageVector {
    return when (flashMode) {
        ImageCapture.FLASH_MODE_ON -> Icons.Filled.FlashOn
        ImageCapture.FLASH_MODE_AUTO -> Icons.Filled.FlashAuto
        else -> Icons.Filled.FlashOff
    }
}

private fun flashContentDescription(flashMode: Int): String {
    return when (flashMode) {
        ImageCapture.FLASH_MODE_ON -> "Flash on"
        ImageCapture.FLASH_MODE_AUTO -> "Flash auto"
        else -> "Flash off"
    }
}

@Composable
private fun ShutterButton(onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(80.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.35f))
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(Color.White)
        )
    }
}

@Composable
private fun CameraPermissionDeniedContent(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(
            text = "Camera permission is required to use this feature.\nPlease grant it in Settings.",
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(32.dp)
        )
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(4.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Color.White
            )
        }
    }
}

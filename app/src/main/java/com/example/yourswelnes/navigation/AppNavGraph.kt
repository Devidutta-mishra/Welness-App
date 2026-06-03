package com.example.yourswelnes.navigation

import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.yourswelnes.feature.auth.presentation.LoginScreen
import com.example.yourswelnes.feature.auth.viewmodel.LoginEvent
import com.example.yourswelnes.feature.auth.viewmodel.LoginViewModel
import com.example.yourswelnes.feature.camera.presentation.CameraPreviewScreen
import com.example.yourswelnes.feature.camera.presentation.CameraScreen
import com.example.yourswelnes.feature.splash.presentation.SplashScreen
import com.example.yourswelnes.feature.splash.viewmodel.SplashNavigationEvent
import com.example.yourswelnes.feature.splash.viewmodel.SplashViewModel
import com.example.yourswelnes.ui.home.HomeNavigationEvent
import com.example.yourswelnes.ui.home.HomeScreen
import com.example.yourswelnes.ui.home.HomeViewModel

@Composable
fun AppNavGraph(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Destinations.SPLASH
    ) {
        composable(Destinations.SPLASH) {
            val viewModel: SplashViewModel = hiltViewModel()
            val uiState by viewModel.uiState.collectAsState()

            LaunchedEffect(viewModel) {
                viewModel.navigationEvent.collect { event ->
                    when (event) {
                        SplashNavigationEvent.NavigateToHome -> {
                            navController.navigate(Destinations.HOME) {
                                popUpTo(Destinations.SPLASH) { inclusive = true }
                            }
                        }
                        SplashNavigationEvent.NavigateToLogin -> {
                            navController.navigate(Destinations.LOGIN) {
                                popUpTo(Destinations.SPLASH) { inclusive = true }
                            }
                        }
                    }
                }
            }

            SplashScreen(uiState = uiState)
        }

        composable(Destinations.LOGIN) {
            val viewModel: LoginViewModel = hiltViewModel()
            val uiState by viewModel.uiState.collectAsState()

            LaunchedEffect(viewModel) {
                viewModel.event.collect { event ->
                    when (event) {
                        LoginEvent.NavigateToHome -> {
                            navController.navigate(Destinations.HOME) {
                                popUpTo(Destinations.LOGIN) { inclusive = true }
                            }
                        }
                    }
                }
            }

            LoginScreen(
                uiState = uiState,
                onPhoneChanged = viewModel::onPhoneChanged,
                onPasswordChanged = viewModel::onPasswordChanged,
                onPasswordVisibilityChanged = viewModel::onPasswordVisibilityChanged,
                onLoginClicked = viewModel::onLoginClicked
            )
        }

        composable(Destinations.HOME) {
            val viewModel: HomeViewModel = hiltViewModel()
            val context = LocalContext.current

            LaunchedEffect(viewModel) {
                viewModel.navigationEvent.collect { event ->
                    when (event) {
                        HomeNavigationEvent.NavigateToLogin -> {
                            navController.navigate(Destinations.LOGIN) {
                                popUpTo(Destinations.HOME) { inclusive = true }
                            }
                        }
                        is HomeNavigationEvent.OpenDashboard -> {
                            CustomTabsIntent.Builder()
                                .setShowTitle(false)
                                .build()
                                .launchUrl(context, Uri.parse(event.url))
                        }
                    }
                }
            }

            HomeScreen(
                viewModel = viewModel,
                onCameraClick = { navController.navigate(Destinations.CAMERA) },
                onFabClick = { navController.navigate(Destinations.CAMERA) },
                onNotificationsClick = { /* TODO: notifications feature */ },
                onLogoutClick = viewModel::onLogoutClicked,
                onDashboardClick = viewModel::openDashboard
            )
        }

        composable(Destinations.CAMERA) {
            CameraScreen(
                onBack = { navController.popBackStack() },
                onPhotoCaptured = { encodedUri ->
                    navController.navigate(Destinations.cameraPreview(encodedUri))
                }
            )
        }

        composable(
            route = Destinations.CAMERA_PREVIEW,
            arguments = listOf(
                navArgument(Destinations.ARG_PHOTO_URI) { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val encodedUri = backStackEntry.arguments?.getString(Destinations.ARG_PHOTO_URI) ?: ""
            val photoUri = Uri.parse(Uri.decode(encodedUri))
            CameraPreviewScreen(
                photoUri = photoUri,
                onRetake = { navController.popBackStack() }
            )
        }
    }
}

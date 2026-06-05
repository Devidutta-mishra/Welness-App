package com.example.yourswelnes.navigation

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.yourswelnes.core.notification.NotificationDeepLink
import com.example.yourswelnes.feature.auth.presentation.LoginEvent
import com.example.yourswelnes.feature.auth.presentation.LoginScreen
import com.example.yourswelnes.feature.auth.presentation.LoginViewModel
import com.example.yourswelnes.feature.biometric.presentation.BiometricLockScreen
import com.example.yourswelnes.feature.biometric.presentation.BiometricViewModel
import com.example.yourswelnes.feature.camera.presentation.CameraPreviewScreen
import com.example.yourswelnes.feature.camera.presentation.CameraScreen
import com.example.yourswelnes.feature.home.presentation.HomeNavigationEvent
import com.example.yourswelnes.feature.home.presentation.HomeScreen
import com.example.yourswelnes.feature.home.presentation.HomeViewModel
import com.example.yourswelnes.feature.location.presentation.LocationPermissionScreen
import com.example.yourswelnes.feature.notifications.presentation.NotificationScreen
import com.example.yourswelnes.feature.requirements.presentation.RequirementsScreen
import com.example.yourswelnes.feature.requirements.presentation.RequirementsViewModel
import com.example.yourswelnes.feature.splash.presentation.SplashNavigationEvent
import com.example.yourswelnes.feature.splash.presentation.SplashScreen
import com.example.yourswelnes.feature.splash.presentation.SplashViewModel
import com.example.yourswelnes.feature.welcome.presentation.WelcomeLandingScreen

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
                        SplashNavigationEvent.NavigateToBiometric -> {
                            navController.navigate(
                                Destinations.requirements(Destinations.BIOMETRIC_LOCK)
                            ) {
                                popUpTo(Destinations.SPLASH) { inclusive = true }
                            }
                        }
                        SplashNavigationEvent.NavigateToLogin -> {
                            navController.navigate(
                                Destinations.requirements(Destinations.WELCOME)
                            ) {
                                popUpTo(Destinations.SPLASH) { inclusive = true }
                            }
                        }
                    }
                }
            }

            SplashScreen(uiState = uiState)
        }

        composable(
            route = Destinations.REQUIREMENTS,
            arguments = listOf(navArgument(Destinations.ARG_NEXT_DEST) { type = NavType.StringType })
        ) { backStackEntry ->
            val nextDest = backStackEntry.arguments?.getString(Destinations.ARG_NEXT_DEST) ?: Destinations.WELCOME
            val viewModel: RequirementsViewModel = hiltViewModel()

            RequirementsScreen(
                nextDestination = nextDest,
                onRequirementsMet = { dest ->
                    navController.navigate(dest) {
                        popUpTo(Destinations.REQUIREMENTS) { inclusive = true }
                    }
                },
                viewModel = viewModel
            )
        }

        composable(Destinations.WELCOME) {
            WelcomeLandingScreen(
                onSignInClick = {
                    navController.navigate(Destinations.LOGIN) {
                        popUpTo(Destinations.WELCOME) { inclusive = true }
                    }
                }
            )
        }

        composable(Destinations.LOGIN) {
            val viewModel: LoginViewModel = hiltViewModel()
            val uiState by viewModel.uiState.collectAsState()

            LaunchedEffect(viewModel) {
                viewModel.event.collect { event ->
                    when (event) {
                        LoginEvent.NavigateToBiometric -> {
                            navController.navigate(Destinations.BIOMETRIC_LOCK) {
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

        composable(Destinations.BIOMETRIC_LOCK) {
            val viewModel: BiometricViewModel = hiltViewModel()

            BiometricLockScreen(
                onAuthenticated = {
                    navController.navigate(Destinations.HOME) {
                        popUpTo(Destinations.BIOMETRIC_LOCK) { inclusive = true }
                    }
                },
                viewModel = viewModel
            )
        }

        composable(Destinations.HOME) {
            val viewModel: HomeViewModel = hiltViewModel()
            val requirementsViewModel: RequirementsViewModel = hiltViewModel()
            val context = LocalContext.current
            val isLockRequired by viewModel.isLockRequired.collectAsState()
            val requirementsState by requirementsViewModel.uiState.collectAsState()
            val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current

            // Recheck requirements each time Home resumes — updates state only, no navigation emitted
            androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
                val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                    if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                        requirementsViewModel.recheckFromBackground()
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }

            // Navigate to requirements gate only when hasChecked=true AND an issue is found.
            // hasChecked prevents the default false-values from triggering on first composition.
            LaunchedEffect(requirementsState.hasAnyIssue) {
                if (requirementsState.hasAnyIssue) {
                    navController.navigate(
                        Destinations.requirements(Destinations.HOME)
                    ) {
                        popUpTo(Destinations.HOME) { inclusive = false }
                    }
                }
            }

            // Navigate to biometric lock when background timeout triggers
            LaunchedEffect(isLockRequired) {
                if (isLockRequired) {
                    navController.navigate(Destinations.BIOMETRIC_LOCK) {
                        popUpTo(Destinations.HOME) { inclusive = false }
                    }
                }
            }

            // Prompt for location permission on first entry
            LaunchedEffect(Unit) {
                val granted = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
                if (!granted) {
                    navController.navigate(Destinations.LOCATION_PERMISSION)
                }
            }

            LaunchedEffect(viewModel) {
                viewModel.navigationEvent.collect { event ->
                    when (event) {
                        HomeNavigationEvent.NavigateToLogin -> {
                            navController.navigate(Destinations.LOGIN) {
                                popUpTo(0) { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                        is HomeNavigationEvent.OpenDashboard -> {
                            try {
                                CustomTabsIntent.Builder()
                                    .setShowTitle(true)
                                    .build()
                                    .launchUrl(context, Uri.parse(event.url))
                            } catch (e: Exception) {
                                try {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(event.url))
                                    )
                                } catch (inner: ActivityNotFoundException) {
                                    Toast.makeText(context, "No browser found.", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    }
                }
            }

            LaunchedEffect(Unit) {
                viewModel.refreshNotifications()
            }

            // React to system notification taps — navigate to Notifications and mark read.
            LaunchedEffect(Unit) {
                NotificationDeepLink.pendingNotificationId.collect { notifId ->
                    if (notifId != null) {
                        navController.navigate(Destinations.NOTIFICATIONS)
                        NotificationDeepLink.consume()
                    }
                }
            }

            HomeScreen(
                viewModel = viewModel,
                onCameraWithGroup = { groupId -> navController.navigate(Destinations.camera(groupId)) },
                onNotificationsClick = { navController.navigate(Destinations.NOTIFICATIONS) },
                onLogoutClick = viewModel::onLogoutClicked,
                onDashboardClick = viewModel::openDashboard
            )
        }

        composable(Destinations.NOTIFICATIONS) {
            val notifViewModel: com.example.yourswelnes.feature.notifications.presentation.NotificationViewModel = hiltViewModel()
            // If we arrived here via a notification tray tap, the pending ID was set before
            // navigation. Mark it read now that the screen is visible.
            LaunchedEffect(Unit) {
                val pendingId = NotificationDeepLink.pendingNotificationId.value
                if (pendingId != null) {
                    notifViewModel.markAsReadExternal(pendingId)
                    NotificationDeepLink.consume()
                }
            }
            NotificationScreen(onBack = { navController.popBackStack() }, viewModel = notifViewModel)
        }

        composable(Destinations.LOCATION_PERMISSION) {
            LocationPermissionScreen(
                onPermissionsGranted = { navController.popBackStack() }
            )
        }

        composable(
            route = Destinations.CAMERA,
            arguments = listOf(navArgument(Destinations.ARG_GROUP_ID) { type = NavType.LongType })
        ) {
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

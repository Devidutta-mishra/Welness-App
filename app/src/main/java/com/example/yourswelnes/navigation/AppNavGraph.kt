package com.example.yourswelnes.navigation

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.yourswelnes.core.notification.NotificationDeepLink
import com.example.yourswelnes.feature.auth.ui.LoginEvent
import com.example.yourswelnes.feature.auth.ui.LoginScreen
import com.example.yourswelnes.feature.auth.ui.LoginViewModel
import com.example.yourswelnes.feature.biometric.ui.BiometricLockScreen
import com.example.yourswelnes.feature.biometric.ui.BiometricViewModel
import com.example.yourswelnes.feature.camera.ui.CameraPreviewScreen
import com.example.yourswelnes.feature.camera.ui.CameraScreen
import com.example.yourswelnes.feature.home.ui.HomeNavigationEvent
import com.example.yourswelnes.feature.home.ui.HomeScreen
import com.example.yourswelnes.feature.home.ui.HomeViewModel
import com.example.yourswelnes.feature.location.ui.LocationStatusViewModel
import com.example.yourswelnes.feature.notifications.ui.NotificationScreen
import com.example.yourswelnes.feature.onboarding.ui.RequirementsScreen
import com.example.yourswelnes.feature.onboarding.ui.RequirementsViewModel
import com.example.yourswelnes.feature.onboarding.ui.SplashNavigationEvent
import com.example.yourswelnes.feature.onboarding.ui.SplashScreen
import com.example.yourswelnes.feature.onboarding.ui.SplashViewModel
import com.example.yourswelnes.feature.onboarding.ui.WelcomeLandingScreen
import com.example.yourswelnes.feature.tracking.ui.PermissionWizardScreen
import com.example.yourswelnes.feature.tracking.ui.TrackingSetupScreen

@Composable
fun AppNavGraph(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Destinations.SPLASH,
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None },
        popEnterTransition = { EnterTransition.None },
        popExitTransition = { ExitTransition.None }
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
            val locationStatusViewModel: LocationStatusViewModel = hiltViewModel()
            val context = LocalContext.current
            val isLockRequired by viewModel.isLockRequired.collectAsState()
            val requirementsState by requirementsViewModel.uiState.collectAsState()
            val locationState by locationStatusViewModel.uiState.collectAsState()
            val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current

            // Every time HOME resumes (from background, from settings, from any other screen),
            // re-check both system requirements (internet, GPS on/off) and all runtime
            // permissions (fine location, background location, notifications, battery optimization).
            // This is the single place that enforces the mandatory-permission contract on every
            // app open, not just on first onboarding.
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        requirementsViewModel.recheckFromBackground()
                        locationStatusViewModel.refreshPermissions()
                        // Re-evaluate tracking health (collection / worker staleness) so the
                        // "Tracking Needs Attention" card appears or clears when the user
                        // returns from OEM settings or after a period in the background.
                        viewModel.recheckTrackingHealth()
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }

            // Navigate to requirements gate (internet + GPS) when an issue is found.
            // hasChecked prevents default false-values from triggering before the first check.
            LaunchedEffect(requirementsState.hasAnyIssue) {
                if (requirementsState.hasAnyIssue) {
                    navController.navigate(
                        Destinations.requirements(Destinations.HOME)
                    ) {
                        popUpTo(Destinations.HOME) { inclusive = false }
                    }
                }
            }

            // Navigate to the mandatory permission wizard whenever ANY of the four mandatory
            // requirements is missing: fine location, background location, notifications, or
            // battery optimization exemption. Fires on every resume via the DisposableEffect
            // above so the check is never skipped — even if the user revokes a permission
            // mid-session or disables battery exemption from system settings. This enforces
            // the "Home is blocked until mandatory requirements are met" rule on every resume.
            //
            // IMPORTANT — re-verify against LIVE state before navigating. When the wizard pops
            // back to HOME, this composable re-composes while LocationStatusViewModel still
            // holds its pre-wizard state (anyRequirementMissing = true), because the async
            // refreshPermissions() has not completed yet. Without the re-check below, that stale
            // value would immediately relaunch the wizard for a redundant OEM-only pass. We call
            // the synchronous refreshPermissions() and re-read uiState.value so we only navigate
            // when the requirement is GENUINELY still missing.
            LaunchedEffect(locationState.anyRequirementMissing) {
                if (locationState.anyRequirementMissing) {
                    locationStatusViewModel.refreshPermissions()
                    if (locationStatusViewModel.uiState.value.anyRequirementMissing) {
                        navController.navigate(Destinations.LOCATION_PERMISSION) {
                            launchSingleTop = true
                        }
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

            // React to notification taps (our own PendingIntent OR FCM SDK auto-notification).
            // notifId > 0 : specific notification → navigate + mark read
            // notifId == -1: FCM tap with no specific ID → just navigate to the list
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
            val notifViewModel: com.example.yourswelnes.feature.notifications.ui.NotificationViewModel = hiltViewModel()
            // If we arrived here via a notification tray tap, the pending ID was set before
            // navigation. Mark it read now that the screen is visible.
            LaunchedEffect(Unit) {
                val pendingId = NotificationDeepLink.pendingNotificationId.value
                if (pendingId != null && pendingId != -1) {
                    // -1 is the FCM sentinel: open the list but no specific item to mark read
                    notifViewModel.markAsReadExternal(pendingId)
                    NotificationDeepLink.consume()
                } else if (pendingId == -1) {
                    NotificationDeepLink.consume()
                }
            }
            NotificationScreen(onBack = { navController.popBackStack() }, viewModel = notifViewModel)
        }

        composable(Destinations.LOCATION_PERMISSION) {
            PermissionWizardScreen(
                onDone = {
                    // If HOME is already in the back stack (mid-session permission revoke),
                    // pop back to it without recreating it.  If HOME is not in the stack
                    // (post-login first-time setup), navigate fresh and clear everything.
                    val popped = navController.popBackStack(Destinations.HOME, inclusive = false)
                    if (!popped) {
                        navController.navigate(Destinations.HOME) {
                            popUpTo(0) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                }
            )
        }

        composable(Destinations.TRACKING_SETUP) {
            TrackingSetupScreen(
                onDone = { navController.popBackStack() },
                blockBackNavigation = false
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

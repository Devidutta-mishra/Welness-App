package com.example.yourswelnes

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.compose.rememberNavController
import com.example.yourswelnes.core.notification.AppNotificationManager
import com.example.yourswelnes.core.notification.NotificationDeepLink
import com.example.yourswelnes.feature.biometric.security.AppLockManager
import com.example.yourswelnes.navigation.AppNavGraph
import com.example.yourswelnes.core.ui.theme.YourswelnesTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import timber.log.Timber

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject lateinit var appLockManager: AppLockManager

    // Must be registered before onStart(); class-level property satisfies that requirement.
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        Timber.tag("MainActivity").i("POST_NOTIFICATIONS permission result: granted=$granted")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Request notification permission right at startup on Android 13+.
        // Without this, FCM notifications never appear on fresh installs — the permission
        // defaults to denied. The PermissionWizard also requests it as a mandatory step, but
        // FCM can register before the wizard runs, so we request it here too. If already
        // granted by the time the wizard runs, the wizard simply skips the notification step.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        handleNotificationIntent(intent)
        setContent {
            YourswelnesTheme {
                val navController = rememberNavController()
                AppNavGraph(navController = navController)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleNotificationIntent(intent)
    }

    private fun handleNotificationIntent(intent: Intent?) {
        when {
            // Foreground FCM: our own PendingIntent via AppNotificationManager.show()
            intent?.action == AppNotificationManager.ACTION_OPEN_NOTIFICATIONS -> {
                val notifId = intent.getIntExtra(AppNotificationManager.EXTRA_NOTIFICATION_ID, -1)
                    .takeIf { it != -1 }
                Timber.tag("MainActivity").i("NOTIFICATION CLICKED: own PendingIntent id=$notifId")
                NotificationDeepLink.set(notifId)
            }
            // Background/killed FCM: SDK auto-notification tap delivers data extras directly
            intent?.hasExtra("notification_id") == true -> {
                val notifIdStr = intent.getStringExtra("notification_id")
                val notifId = notifIdStr?.toIntOrNull() ?: -1
                Timber.tag("MainActivity").i("NOTIFICATION CLICKED: FCM auto-notification id=$notifIdStr")
                NotificationDeepLink.set(notifId)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        appLockManager.onAppBackgrounded()
    }

    override fun onStart() {
        super.onStart()
        appLockManager.onAppForegrounded()
    }
}

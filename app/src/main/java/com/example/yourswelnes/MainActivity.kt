package com.example.yourswelnes

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.compose.rememberNavController
import com.example.yourswelnes.core.notification.AppNotificationManager
import com.example.yourswelnes.core.notification.NotificationDeepLink
import com.example.yourswelnes.feature.biometric.security.AppLockManager
import com.example.yourswelnes.navigation.AppNavGraph
import com.example.yourswelnes.ui.theme.YourswelnesTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject lateinit var appLockManager: AppLockManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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
            // Tapped our own notification (shown by AppNotificationManager or onMessageReceived)
            intent?.action == AppNotificationManager.ACTION_OPEN_NOTIFICATIONS -> {
                val notifId = intent.getIntExtra(AppNotificationManager.EXTRA_NOTIFICATION_ID, -1)
                    .takeIf { it != -1 }
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

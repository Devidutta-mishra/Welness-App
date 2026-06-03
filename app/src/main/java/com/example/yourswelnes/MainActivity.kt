package com.example.yourswelnes

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.compose.rememberNavController
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
        setContent {
            YourswelnesTheme {
                val navController = rememberNavController()
                AppNavGraph(navController = navController)
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

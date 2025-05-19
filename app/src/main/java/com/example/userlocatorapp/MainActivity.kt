package com.example.userlocatorapp

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import com.example.userlocatorapp.ui.theme.UserLocatorAppTheme
import com.google.firebase.FirebaseApp

// Navegación de pantallas
sealed class Screen {
    object Auth : Screen()
    object MainMenu : Screen()
    object Profile : Screen()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FirebaseApp.initializeApp(this)

        // Solicitar permisos de ubicación
        val requestPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            // Puedes mostrar mensaje si no es concedido
        }
        requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)

        // Composición UI
        setContent {
            UserLocatorAppTheme {
                var currentScreen by remember { mutableStateOf<Screen>(Screen.Auth) }

                when (currentScreen) {
                    is Screen.Auth -> AuthScreen(
                        onAuthSuccess = { currentScreen = Screen.MainMenu }
                    )

                    is Screen.MainMenu -> MainMenuScreen(
                        onLogout = { currentScreen = Screen.Auth },
                        onEditProfile = { currentScreen = Screen.Profile }
                    )

                    is Screen.Profile -> ProfileScreen(
                        onBackToMenu = { currentScreen = Screen.MainMenu },
                        onLogout = { currentScreen = Screen.Auth }
                    )
                }
            }
        }
    }
}

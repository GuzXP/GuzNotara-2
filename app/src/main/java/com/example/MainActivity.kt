package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.example.ai.configureAppCheck
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.RecordingStudioScreen
import com.example.ui.screens.TranscriptWorkspaceScreen
import com.example.ui.theme.GuzNotaraTheme
import com.example.ui.viewmodel.MainViewModel

sealed class Screen {
    data object Home : Screen()
    data object Recording : Screen()
    data class Workspace(val meetingId: String) : Screen()
}

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Mandatory App Check configuration with launch intent before any UI or Firebase calls
        configureAppCheck(this, intent)

        enableEdgeToEdge()

        setContent {
            GuzNotaraTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    GuzNotaraApp(viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
fun GuzNotaraApp(viewModel: MainViewModel) {
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Home) }

    // Request necessary runtime permissions
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // Permissions handled; recording service handles any hardware fallback gracefully
    }

    LaunchedEffect(Unit) {
        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionLauncher.launch(permissions.toTypedArray())
    }

    AnimatedContent(
        targetState = currentScreen,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "screen_transition"
    ) { screen ->
        when (screen) {
            is Screen.Home -> {
                HomeScreen(
                    viewModel = viewModel,
                    onNavigateToRecording = {
                        currentScreen = Screen.Recording
                    },
                    onNavigateToWorkspace = { meetingId ->
                        currentScreen = Screen.Workspace(meetingId)
                    }
                )
            }
            is Screen.Recording -> {
                RecordingStudioScreen(
                    viewModel = viewModel,
                    onNavigateBack = {
                        currentScreen = Screen.Home
                    },
                    onRecordingFinished = { meetingId ->
                        currentScreen = Screen.Workspace(meetingId)
                    }
                )
            }
            is Screen.Workspace -> {
                TranscriptWorkspaceScreen(
                    meetingId = screen.meetingId,
                    viewModel = viewModel,
                    onNavigateBack = {
                        currentScreen = Screen.Home
                    }
                )
            }
        }
    }
}

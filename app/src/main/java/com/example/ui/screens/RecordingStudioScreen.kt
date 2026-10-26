package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.recording.RecordingStatus
import com.example.ui.components.AudioWaveformVisualizer
import com.example.ui.components.StatusBadge
import com.example.ui.theme.AcousticAmber
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.ElectricSage
import com.example.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingStudioScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit,
    onRecordingFinished: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val activeMeeting by viewModel.activeMeeting.collectAsStateWithLifecycle()
    val recordingStatus by viewModel.recordingStatus.collectAsStateWithLifecycle()
    val elapsedTimeMs by viewModel.recordingElapsedTimeMs.collectAsStateWithLifecycle()
    val amplitude by viewModel.currentAmplitude.collectAsStateWithLifecycle()

    var showCancelDialog by remember { mutableStateOf(false) }

    // Format timer
    val seconds = (elapsedTimeMs / 1000) % 60
    val minutes = (elapsedTimeMs / (1000 * 60)) % 60
    val hours = (elapsedTimeMs / (1000 * 60 * 60))
    val timerText = if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }

    // Intercept back button to avoid accidental termination
    BackHandler {
        if (recordingStatus == RecordingStatus.RECORDING || recordingStatus == RecordingStatus.PAUSED) {
            showCancelDialog = true
        } else {
            onNavigateBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = activeMeeting?.title ?: "Studio Perekaman",
                        maxLines = 1,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (recordingStatus == RecordingStatus.RECORDING || recordingStatus == RecordingStatus.PAUSED) {
                            showCancelDialog = true
                        } else {
                            onNavigateBack()
                        }
                    }) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                },
                actions = {
                    StatusBadge(
                        status = if (recordingStatus == RecordingStatus.PAUSED) "PAUSED" else "RECORDING",
                        modifier = Modifier.padding(end = 12.dp)
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Background Foreground Service badge
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = ElectricSage,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Foreground Service Aktif • Audio aman tersimpan bertahap di perangkat.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(36.dp))

            // Giant Monospaced Timer
            Text(
                text = timerText,
                fontFamily = FontFamily.Monospace,
                fontSize = 54.sp,
                fontWeight = FontWeight.Bold,
                color = if (recordingStatus == RecordingStatus.PAUSED) AcousticAmber else MaterialTheme.colorScheme.onBackground,
                letterSpacing = 2.sp,
                modifier = Modifier.testTag("recording_timer")
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (recordingStatus == RecordingStatus.PAUSED) "Perekaman Dijeda" else "Merekam Percakapan Verbatim...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Dynamic Audio Waveform Canvas
            AudioWaveformVisualizer(
                amplitude = amplitude,
                isRecording = recordingStatus == RecordingStatus.RECORDING,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
            )

            Spacer(modifier = Modifier.height(36.dp))

            // Info Card about verbatim fidelity
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Prinsip Zero-Omission GuzNotara",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = AcousticAmber
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Setiap kata, pertanyaan, sanggahan, dan obrolan santai akan tetap terekam secara utuh tanpa pemotongan AI yang merugikan.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Bottom Action Controls
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                // Pause / Resume Button
                Button(
                    onClick = {
                        if (recordingStatus == RecordingStatus.RECORDING) {
                            viewModel.pauseRecording()
                        } else {
                            viewModel.resumeRecording()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .height(56.dp)
                        .testTag("pause_resume_button")
                ) {
                    Icon(
                        imageVector = if (recordingStatus == RecordingStatus.PAUSED) Icons.Default.PlayArrow else Icons.Default.Pause,
                        contentDescription = if (recordingStatus == RecordingStatus.PAUSED) "Lanjut" else "Jeda",
                        tint = AcousticAmber,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (recordingStatus == RecordingStatus.PAUSED) "Lanjut" else "Jeda",
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Stop & Transcribe Button
                Button(
                    onClick = {
                        viewModel.stopRecording { meetingId ->
                            onRecordingFinished(meetingId)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CrimsonAlert,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .height(56.dp)
                        .weight(1f)
                        .testTag("stop_recording_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = "Selesai & Transkrip",
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Selesai & Transkrip",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }

    // Cancel / Exit confirmation dialog
    if (showCancelDialog) {
        AlertDialog(
            onDismissRequest = { showCancelDialog = false },
            title = { Text("Tinggalkan Halaman Rekam?") },
            text = {
                Text("Perekaman audio tetap berjalan di latar belakang (Foreground Service). Anda dapat kembali kapan saja untuk menghentikannya.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showCancelDialog = false
                        onNavigateBack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Tinggalkan Halaman", color = MaterialTheme.colorScheme.onPrimary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelDialog = false }) {
                    Text("Tetap di Studio")
                }
            }
        )
    }
}

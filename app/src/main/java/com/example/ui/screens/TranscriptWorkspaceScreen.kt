package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.TranscriptSegmentEntity
import com.example.ui.components.SpeakerBadge
import com.example.ui.components.StatusBadge
import com.example.ui.components.TimecodeChip
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import com.example.ui.theme.CrimsonAlert
import java.util.Locale
import com.example.ui.theme.AcousticAmber
import com.example.ui.theme.ElectricSage
import com.example.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranscriptWorkspaceScreen(
    meetingId: String,
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activeMeeting by viewModel.activeMeeting.collectAsStateWithLifecycle()
    val segments by viewModel.activeSegments.collectAsStateWithLifecycle()
    val document by viewModel.activeDocument.collectAsStateWithLifecycle()
    val isAiProcessing by viewModel.isAiProcessing.collectAsStateWithLifecycle()
    val aiProgressMessage by viewModel.aiProgressMessage.collectAsStateWithLifecycle()
    val lastDocumentProvider by viewModel.lastDocumentProvider.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableStateOf(0) } // 0: Verbatim, 1: Organized Document
    var segmentSearchQuery by remember { mutableStateOf("") }

    // Dialog states
    var segmentToEdit by remember { mutableStateOf<TranscriptSegmentEntity?>(null) }
    var speakerToRename by remember { mutableStateOf<String?>(null) }
    var showAiInstructionDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showGroqKeyDialog by remember { mutableStateOf(false) }

    LaunchedEffect(meetingId) {
        viewModel.selectMeeting(meetingId)
    }

    val filteredSegments = remember(segments, segmentSearchQuery) {
        if (segmentSearchQuery.isBlank()) segments
        else segments.filter {
            it.text.contains(segmentSearchQuery, ignoreCase = true) ||
                    it.speaker.contains(segmentSearchQuery, ignoreCase = true)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = activeMeeting?.title ?: "Transkrip Pertemuan",
                            maxLines = 1,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (selectedTab == 0) "Transkrip Verbatim (Lengkap)" else "Dokumen Rapi (AI Organized)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showExportDialog = true },
                        modifier = Modifier.testTag("action_export_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Ekspor & Bagikan",
                            tint = AcousticAmber
                        )
                    }
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
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // AI Processing Progress Banner
            AnimatedVisibility(visible = isAiProcessing) {
                Surface(
                    color = AcousticAmber.copy(alpha = 0.15f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        CircularProgressIndicator(
                            color = AcousticAmber,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = aiProgressMessage,
                            fontSize = 13.sp,
                            color = AcousticAmber,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Dual-Mode High-Contrast Segmented Tabs
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = AcousticAmber
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Text(
                            text = "Transkrip Verbatim",
                            fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 14.sp
                        )
                    },
                    modifier = Modifier.testTag("tab_verbatim")
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = ElectricSage,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Dokumen Tertata",
                                fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 14.sp
                            )
                        }
                    },
                    modifier = Modifier.testTag("tab_organized")
                )
            }

            // Tab Content
            if (selectedTab == 0) {
                // VERBATIM MODE
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                ) {
                    val isAudioAvail = viewModel.isAudioAvailable(meetingId)
                    val audioBytes = viewModel.getAudioSizeBytes(meetingId)
                    val audioSizeMb = (audioBytes / (1024.0 * 1024.0))

                    // Audio Storage Status Banner
                    if (isAudioAvail) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = AcousticAmber.copy(alpha = 0.12f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.GraphicEq,
                                        contentDescription = null,
                                        tint = AcousticAmber,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Audio Sementara: ${String.format(Locale.US, "%.1f MB", audioSizeMb)}",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                TextButton(
                                    onClick = { viewModel.deleteAudioOnly(meetingId) },
                                    modifier = Modifier.testTag("delete_audio_button")
                                ) {
                                    Text("Hapus Audio Saja", fontSize = 12.sp, color = CrimsonAlert)
                                }
                            }
                        }
                    } else if (segments.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = ElectricSage.copy(alpha = 0.12f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = ElectricSage,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Audio sementara dibersihkan (Transkrip Room tersimpan aman).",
                                    fontSize = 12.sp,
                                    color = ElectricSage
                                )
                            }
                        }
                    }

                    // Error banner if transcription encountered an issue
                    if (activeMeeting?.status == "ERROR") {
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = CrimsonAlert.copy(alpha = 0.12f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                                .testTag("transcription_error_card")
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = CrimsonAlert,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Transkripsi Mengalami Kendala",
                                        fontWeight = FontWeight.Bold,
                                        color = CrimsonAlert,
                                        fontSize = 14.sp
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = activeMeeting?.errorMessage ?: "Gagal memproses rekaman audio ke teks.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Berkas audio rekaman tetap utuh dan aman di perangkat untuk dicoba ulang.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Button(
                                        onClick = { viewModel.processTranscription(meetingId) },
                                        enabled = !isAiProcessing,
                                        colors = ButtonDefaults.buttonColors(containerColor = CrimsonAlert),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("retry_transcription_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Coba Ulang", fontSize = 12.sp)
                                    }
                                    if (isAudioAvail) {
                                        Button(
                                            onClick = { viewModel.deleteAudioOnly(meetingId) },
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(
                                                "Hapus Audio Saja",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Search in verbatim
                    OutlinedTextField(
                        value = segmentSearchQuery,
                        onValueChange = { segmentSearchQuery = it },
                        placeholder = { Text("Cari kata dalam transkrip...") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Cari kata",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedBorderColor = AcousticAmber,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                    )

                    // Re-process / Organize banner button
                    Button(
                        onClick = { viewModel.organizeDocumentWithThinking(meetingId) },
                        enabled = !isAiProcessing,
                        colors = ButtonDefaults.buttonColors(containerColor = AcousticAmber),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .testTag("organize_document_button")
                    ) {
                        Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.background)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Tata Dokumen dengan Gemini 4 Argon",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.background
                        )
                    }

                    if (filteredSegments.isEmpty()) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = if (segmentSearchQuery.isNotBlank()) "Tidak ada ucapan yang cocok" else "Transkrip belum diproses",
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(
                                    onClick = { viewModel.processTranscription(meetingId) },
                                    enabled = !isAiProcessing,
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Icon(imageVector = Icons.Default.Refresh, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Jalankan Transkripsi Verbatim")
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(bottom = 40.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(filteredSegments, key = { it.id }) { segment ->
                                VerbatimSegmentItem(
                                    segment = segment,
                                    onEdit = { segmentToEdit = segment },
                                    onRenameSpeaker = { speakerToRename = segment.speaker }
                                )
                            }
                        }
                    }
                }
            } else {
                // ORGANIZED DOCUMENT MODE
                val organizedText = document?.organizedContent.orEmpty()
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    val currentProvider = lastDocumentProvider ?: "Gemini 4 Argon (Thinking Mode)"
                    // Thinking Mode & Provider Badge
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = ElectricSage.copy(alpha = 0.15f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = ElectricSage,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Ditata dengan: $currentProvider • Seluruh konteks & dialog tetap utuh.",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = ElectricSage
                                )
                            }
                            IconButton(
                                onClick = { showGroqKeyDialog = true },
                                modifier = Modifier
                                    .size(28.dp)
                                    .testTag("groq_settings_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "Pengaturan Fallback Groq",
                                    tint = ElectricSage,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Action buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { showAiInstructionDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                tint = AcousticAmber,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Instruksi AI", color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
                        }

                        Button(
                            onClick = { viewModel.organizeDocumentWithThinking(meetingId) },
                            enabled = !isAiProcessing,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                tint = AcousticAmber,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Tata Ulang", color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Long-form editorial document body
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 32.dp)
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text(
                                text = organizedText,
                                style = MaterialTheme.typography.bodyLarge,
                                lineHeight = 26.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }

    // Edit Segment Dialog
    segmentToEdit?.let { segment ->
        var editedText by remember { mutableStateOf(segment.text) }
        AlertDialog(
            onDismissRequest = { segmentToEdit = null },
            title = { Text("Koreksi Ucapan Transkrip") },
            text = {
                Column {
                    Text(
                        text = "Edit ucapan '${segment.speaker}' tanpa mengubah timestamp:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editedText,
                        onValueChange = { editedText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .testTag("edit_segment_text_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateSegmentText(segment.id, editedText, meetingId)
                        segmentToEdit = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AcousticAmber)
                ) {
                    Text("Simpan Koreksi", color = MaterialTheme.colorScheme.background, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { segmentToEdit = null }) {
                    Text("Batal")
                }
            }
        )
    }

    // Rename Speaker Dialog
    speakerToRename?.let { oldSpeaker ->
        var newSpeakerName by remember { mutableStateOf(oldSpeaker) }
        AlertDialog(
            onDismissRequest = { speakerToRename = null },
            title = { Text("Ubah Nama Pembicara") },
            text = {
                Column {
                    Text(
                        text = "Ganti nama '$oldSpeaker' menjadi nama peserta asli untuk semua segmen ucapan terkait:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = newSpeakerName,
                        onValueChange = { newSpeakerName = it },
                        label = { Text("Nama Baru") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("rename_speaker_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newSpeakerName.isNotBlank()) {
                            viewModel.renameSpeaker(meetingId, oldSpeaker, newSpeakerName.trim())
                        }
                        speakerToRename = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AcousticAmber)
                ) {
                    Text("Perbarui Semua", color = MaterialTheme.colorScheme.background, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { speakerToRename = null }) {
                    Text("Batal")
                }
            }
        )
    }

    // Custom AI Instruction Dialog
    if (showAiInstructionDialog) {
        var instructionText by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAiInstructionDialog = false },
            title = { Text("Instruksi Kustom AI") },
            text = {
                Column {
                    Text(
                        text = "Berikan arahan tambahan untuk gaya tata dokumen (misal: 'Sorot keputusan penting dengan huruf tebal' atau 'Jadikan salam pembuka lebih formal'):",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = instructionText,
                        onValueChange = { instructionText = it },
                        placeholder = { Text("Tulis instruksi...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (instructionText.isNotBlank()) {
                            viewModel.applyCustomInstruction(meetingId, instructionText.trim())
                        }
                        showAiInstructionDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AcousticAmber)
                ) {
                    Text("Terapkan", color = MaterialTheme.colorScheme.background, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAiInstructionDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }

    // Export Options Dialog
    if (showExportDialog) {
        val currentMeeting = activeMeeting
        val currentText = if (selectedTab == 1) document?.organizedContent.orEmpty() else document?.verbatimContent.orEmpty()
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("Ekspor Dokumen Pertemuan") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Pilih format dokumen untuk diekspor dan dibagikan:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Button(
                        onClick = {
                            showExportDialog = false
                            if (currentMeeting != null) {
                                viewModel.exportPdf(context, currentMeeting, currentText, selectedTab == 1)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("export_pdf_button")
                    ) {
                        Text("Dokumen PDF (.pdf)", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                    }

                    Button(
                        onClick = {
                            showExportDialog = false
                            if (currentMeeting != null) {
                                viewModel.exportMarkdown(context, currentMeeting, currentText)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("export_markdown_button")
                    ) {
                        Text("Dokumen Markdown (.md)", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                    }

                    Button(
                        onClick = {
                            showExportDialog = false
                            if (currentMeeting != null) {
                                viewModel.exportPlainText(context, currentMeeting, currentText)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("export_txt_button")
                    ) {
                        Text("Teks Polos (.txt)", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showExportDialog = false }) {
                    Text("Tutup")
                }
            }
        )
    }

    // Groq Fallback Settings Dialog
    if (showGroqKeyDialog) {
        var apiKeyInput by remember { mutableStateOf(viewModel.getGroqApiKey()) }
        AlertDialog(
            onDismissRequest = { showGroqKeyDialog = false },
            title = { Text("Pengaturan Fallback Groq") },
            text = {
                Column {
                    Text(
                        text = "GuzNotara menggunakan Llama 3.3 70B Versatile (via Groq) secara otomatis sebagai failover ketika Gemini Argon mengalami rate-limit atau kegagalan transient.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = apiKeyInput,
                        onValueChange = { apiKeyInput = it },
                        label = { Text("Groq API Key (gsk_...)") },
                        placeholder = { Text("Masukkan API Key Groq") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("groq_api_key_input")
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Kunci disimpan secara lokal di SharedPreferences aplikasi perangkat Anda.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.setGroqApiKey(apiKeyInput.trim())
                        showGroqKeyDialog = false
                    },
                    modifier = Modifier.testTag("save_groq_key_button")
                ) {
                    Text("Simpan")
                }
            },
            dismissButton = {
                TextButton(onClick = { showGroqKeyDialog = false }) {
                    Text("Tutup")
                }
            }
        )
    }
}

@Composable
fun VerbatimSegmentItem(
    segment: TranscriptSegmentEntity,
    onEdit: () -> Unit,
    onRenameSpeaker: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier
            .fillMaxWidth()
            .testTag("segment_item_${segment.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TimecodeChip(timeMs = segment.startTimeMs)
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(modifier = Modifier.clickable(onClick = onRenameSpeaker)) {
                        SpeakerBadge(speakerName = segment.speaker)
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (segment.isEdited) {
                        Text(
                            text = "(diedit)",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 6.dp)
                        )
                    }
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Ucapan",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = segment.text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 22.sp
            )
        }
    }
}

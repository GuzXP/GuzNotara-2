package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.GeminiAiManager
import com.example.data.model.MeetingDocumentEntity
import com.example.data.model.MeetingEntity
import com.example.data.model.TranscriptSegmentEntity
import com.example.data.repository.MeetingRepository
import com.example.export.ExportHelper
import com.example.recording.AudioCleanupHelper
import com.example.recording.AudioRecordingService
import com.example.recording.RecordingStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MeetingRepository(application)
    private val aiManager = GeminiAiManager(application)

    val allMeetings: StateFlow<List<MeetingEntity>> = repository.allMeetings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _selectedMeetingId = MutableStateFlow<String?>(null)
    val selectedMeetingId: StateFlow<String?> = _selectedMeetingId.asStateFlow()

    private val _activeMeeting = MutableStateFlow<MeetingEntity?>(null)
    val activeMeeting: StateFlow<MeetingEntity?> = _activeMeeting.asStateFlow()

    private val _activeSegments = MutableStateFlow<List<TranscriptSegmentEntity>>(emptyList())
    val activeSegments: StateFlow<List<TranscriptSegmentEntity>> = _activeSegments.asStateFlow()

    private val _activeDocument = MutableStateFlow<MeetingDocumentEntity?>(null)
    val activeDocument: StateFlow<MeetingDocumentEntity?> = _activeDocument.asStateFlow()

    // Recording Service observation
    val recordingStatus: StateFlow<RecordingStatus> = AudioRecordingService.recordingStatus
    val recordingElapsedTimeMs: StateFlow<Long> = AudioRecordingService.elapsedTimeMs
    val currentAmplitude: StateFlow<Float> = AudioRecordingService.currentAmplitude
    val activeRecordingMeetingId: StateFlow<String?> = AudioRecordingService.activeMeetingId

    private val _isAiProcessing = MutableStateFlow(false)
    val isAiProcessing: StateFlow<Boolean> = _isAiProcessing.asStateFlow()

    private val _aiProgressMessage = MutableStateFlow("")
    val aiProgressMessage: StateFlow<String> = _aiProgressMessage.asStateFlow()

    private val _lastDocumentProvider = MutableStateFlow<String?>(null)
    val lastDocumentProvider: StateFlow<String?> = _lastDocumentProvider.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init {
        viewModelScope.launch {
            allMeetings.collect { meetings ->
                if (meetings.isNotEmpty()) {
                    val activeIds = meetings
                        .filter { it.status == "RECORDING" || it.status == "RECORDED" || it.status == "TRANSCRIBING" || it.status == "ERROR" }
                        .map { it.id }
                        .toSet()
                    AudioCleanupHelper.cleanupStaleAudio(getApplication(), activeIds)
                }
            }
        }

        viewModelScope.launch {
            AudioRecordingService.recordingStatus.collect { status ->
                if (status == RecordingStatus.ERROR) {
                    val activeId = AudioRecordingService.finalizedMeetingId.value ?: _selectedMeetingId.value
                    val err = AudioRecordingService.recordingErrorMessage.value ?: "Perekam suara gagal diinisialisasi."
                    if (activeId != null) {
                        repository.updateMeetingError(activeId, "ERROR", err)
                    }
                    _errorMessage.value = err
                }
            }
        }
    }

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun selectMeeting(meetingId: String) {
        _selectedMeetingId.value = meetingId
        viewModelScope.launch {
            repository.getMeeting(meetingId).collect { meeting ->
                _activeMeeting.value = meeting
            }
        }
        viewModelScope.launch {
            repository.getSegments(meetingId).collect { segments ->
                _activeSegments.value = segments
            }
        }
        viewModelScope.launch {
            repository.getDocument(meetingId).collect { doc ->
                _activeDocument.value = doc
            }
        }
    }

    fun startRecording(title: String) {
        val meetingId = UUID.randomUUID().toString()
        val context = getApplication<Application>()
        val tempAudioDir = AudioCleanupHelper.getTempAudioDir(context, meetingId)
        val audioPath = File(tempAudioDir, "chunk_000.m4a").absolutePath

        viewModelScope.launch {
            repository.createMeeting(meetingId, title, audioPath)
            selectMeeting(meetingId)

            val intent = Intent(context, AudioRecordingService::class.java).apply {
                action = AudioRecordingService.ACTION_START
                putExtra(AudioRecordingService.EXTRA_MEETING_ID, meetingId)
                putExtra(AudioRecordingService.EXTRA_MEETING_TITLE, title)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    fun pauseRecording() {
        val context = getApplication<Application>()
        val intent = Intent(context, AudioRecordingService::class.java).apply {
            action = AudioRecordingService.ACTION_PAUSE
        }
        context.startService(intent)
    }

    fun resumeRecording() {
        val context = getApplication<Application>()
        val intent = Intent(context, AudioRecordingService::class.java).apply {
            action = AudioRecordingService.ACTION_RESUME
        }
        context.startService(intent)
    }

    fun stopRecording(onComplete: (String) -> Unit) {
        val meetingId = _selectedMeetingId.value ?: return
        val finalDuration = recordingElapsedTimeMs.value
        val context = getApplication<Application>()
        val intent = Intent(context, AudioRecordingService::class.java).apply {
            action = AudioRecordingService.ACTION_STOP
        }
        context.startService(intent)

        viewModelScope.launch {
            // Await confirmation that the recorder has stopped and the final chunk is finalized
            AudioRecordingService.recordingStatus.first {
                it == RecordingStatus.STOPPED || it == RecordingStatus.IDLE || it == RecordingStatus.ERROR
            }

            if (AudioRecordingService.recordingStatus.value == RecordingStatus.ERROR) {
                val err = AudioRecordingService.recordingErrorMessage.value ?: "Perekaman gagal."
                repository.updateMeetingError(meetingId, "ERROR", err)
                _errorMessage.value = err
                return@launch
            }

            repository.updateMeetingStatus(meetingId, "RECORDED", finalDuration)
            // Automatically launch transcription with finalized audio
            processTranscription(meetingId)
            onComplete(meetingId)
        }
    }

    fun processTranscription(meetingId: String) {
        viewModelScope.launch {
            _isAiProcessing.value = true
            _aiProgressMessage.value = "Mentranskripsi rekaman audio secara verbatim..."
            val context = getApplication<Application>()
            try {
                val meeting = repository.getMeetingSync(meetingId) ?: return@launch
                repository.updateMeetingError(meetingId, "TRANSCRIBING", null)

                val audioFiles = AudioCleanupHelper.getAudioFilesForMeeting(context, meetingId)
                if (audioFiles.isEmpty()) {
                    throw com.example.ai.TranscriptionException("File audio tidak ditemukan di penyimpanan lokal.")
                }

                val segments = aiManager.transcribeMeetingAudio(
                    meetingId = meetingId,
                    meetingTitle = meeting.title,
                    audioFiles = audioFiles,
                    durationMs = meeting.durationMs
                )

                // 1. Persist segments and verbatim document to SQLite first
                repository.saveSegments(segments)

                // 2. Validate saved segments
                val savedSegments = repository.getSegmentsSync(meetingId)
                if (savedSegments.isNotEmpty()) {
                    // 3. Only after verified persistence, safely clean up temporary audio files
                    AudioCleanupHelper.cleanupMeetingAudio(context, meetingId)
                    repository.clearAudioPath(meetingId)
                    repository.updateMeetingError(meetingId, "COMPLETED", null)
                    _aiProgressMessage.value = "Transkripsi berhasil dan audio sementara dibersihkan."
                } else {
                    throw com.example.ai.TranscriptionException("Gagal memvalidasi penyimpanan transkrip ke database.")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val errorMsg = e.localizedMessage ?: "Terjadi kesalahan saat transkripsi."
                repository.updateMeetingError(meetingId, "ERROR", errorMsg)
                _errorMessage.value = "Transkripsi gagal: $errorMsg. Audio tetap tersimpan untuk dicoba ulang."
                // Note: Temporary audio files are explicitly PRESERVED on disk so user can retry!
            } finally {
                _isAiProcessing.value = false
            }
        }
    }

    fun organizeDocumentWithThinking(meetingId: String) {
        viewModelScope.launch {
            _isAiProcessing.value = true
            _aiProgressMessage.value = "Menata dokumen lengkap dengan Gemini 4 Argon..."
            try {
                val meeting = repository.getMeetingSync(meetingId) ?: return@launch
                val doc = repository.getDocumentSync(meetingId)
                val verbatimText = doc?.verbatimContent.orEmpty()

                if (verbatimText.isBlank()) {
                    _errorMessage.value = "Belum ada transkrip verbatim untuk ditata."
                    return@launch
                }

                val organized = aiManager.organizeDocument(
                    meetingTitle = meeting.title,
                    verbatimTranscript = verbatimText,
                    onProviderSelected = { provider ->
                        _lastDocumentProvider.value = provider
                        _aiProgressMessage.value = "Menata dengan $provider..."
                    }
                )

                repository.saveOrganizedDocument(meetingId, organized)
                repository.setDocumentMode(meetingId, "ORGANIZED")
                val providerLabel = _lastDocumentProvider.value ?: "AI"
                _aiProgressMessage.value = "Dokumen berhasil ditata ($providerLabel)!"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _errorMessage.value = "Gagal menata dokumen: ${e.localizedMessage}"
            } finally {
                _isAiProcessing.value = false
            }
        }
    }

    fun deleteAudioOnly(meetingId: String) {
        viewModelScope.launch {
            val context = getApplication<Application>()
            AudioCleanupHelper.cleanupMeetingAudio(context, meetingId)
            repository.clearAudioPath(meetingId)
            Toast.makeText(context, "Audio sementara berhasil dibersihkan.", Toast.LENGTH_SHORT).show()
        }
    }

    fun isAudioAvailable(meetingId: String): Boolean {
        val context = getApplication<Application>()
        return AudioCleanupHelper.isAudioAvailable(context, meetingId)
    }

    fun getAudioSizeBytes(meetingId: String): Long {
        val context = getApplication<Application>()
        return AudioCleanupHelper.getTotalAudioSizeBytes(context, meetingId)
    }

    fun setGroqApiKey(apiKey: String) {
        aiManager.groqProvider.saveCustomApiKey(apiKey)
    }

    fun getGroqApiKey(): String {
        return aiManager.groqProvider.getCustomApiKey()
    }

    fun applyCustomInstruction(meetingId: String, instruction: String) {
        viewModelScope.launch {
            _isAiProcessing.value = true
            _aiProgressMessage.value = "Menerapkan instruksi kustom..."
            try {
                val doc = repository.getDocumentSync(meetingId) ?: return@launch
                val currentText = if (doc.currentMode == "ORGANIZED") doc.organizedContent else doc.verbatimContent
                val refined = aiManager.applyCustomInstruction(currentText, instruction)

                if (doc.currentMode == "ORGANIZED") {
                    repository.saveOrganizedDocument(meetingId, refined)
                } else {
                    // Update verbatim
                    repository.updateMeetingStatus(meetingId, "COMPLETED", 0L)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _errorMessage.value = "Gagal memproses instruksi: ${e.localizedMessage}"
            } finally {
                _isAiProcessing.value = false
            }
        }
    }

    fun updateSegmentText(segmentId: String, newText: String, meetingId: String) {
        viewModelScope.launch {
            repository.updateSegmentText(segmentId, newText, meetingId)
        }
    }

    fun renameSpeaker(meetingId: String, oldSpeaker: String, newSpeaker: String) {
        viewModelScope.launch {
            repository.renameSpeaker(meetingId, oldSpeaker, newSpeaker)
        }
    }

    fun setDocumentMode(meetingId: String, mode: String) {
        viewModelScope.launch {
            repository.setDocumentMode(meetingId, mode)
        }
    }

    fun deleteMeeting(meetingId: String) {
        viewModelScope.launch {
            repository.deleteMeeting(meetingId)
            if (_selectedMeetingId.value == meetingId) {
                _selectedMeetingId.value = null
                _activeMeeting.value = null
                _activeSegments.value = emptyList()
                _activeDocument.value = null
            }
        }
    }

    fun exportPdf(context: Context, meeting: MeetingEntity, content: String, isOrganized: Boolean) {
        try {
            val file = ExportHelper.generatePdf(context, meeting, content, isOrganized)
            ExportHelper.shareFile(context, file, "application/pdf")
        } catch (e: Exception) {
            Toast.makeText(context, "Gagal membuat PDF: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    fun exportMarkdown(context: Context, meeting: MeetingEntity, content: String) {
        try {
            val file = ExportHelper.generateMarkdown(context, meeting, content)
            ExportHelper.shareFile(context, file, "text/markdown")
        } catch (e: Exception) {
            Toast.makeText(context, "Gagal mengekspor Markdown: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    fun exportPlainText(context: Context, meeting: MeetingEntity, content: String) {
        try {
            val file = ExportHelper.generatePlainText(context, meeting, content)
            ExportHelper.shareFile(context, file, "text/plain")
        } catch (e: Exception) {
            Toast.makeText(context, "Gagal mengekspor Text: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }
}

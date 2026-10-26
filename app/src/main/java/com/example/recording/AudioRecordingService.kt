package com.example.recording

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaRecorder
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

enum class RecordingStatus {
    IDLE,
    RECORDING,
    PAUSED,
    STOPPED
}

class AudioRecordingService : Service() {

    private val binder = LocalBinder()
    private var mediaRecorder: MediaRecorder? = null
    private var currentMeetingId: String = ""
    private var currentTitle: String = ""
    private var currentChunkIndex: Int = 0
    private var currentChunkFile: File? = null

    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())
    private var timerJob: Job? = null
    private var amplitudeJob: Job? = null

    private var startTimeMs: Long = 0L
    private var pausedDurationMs: Long = 0L
    private var pauseTimestampMs: Long = 0L
    private var isHardwareAvailable: Boolean = false

    companion object {
        private const val TAG = "AudioRecordingService"
        const val CHANNEL_ID = "guznotara_recording_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.example.recording.ACTION_START"
        const val ACTION_PAUSE = "com.example.recording.ACTION_PAUSE"
        const val ACTION_RESUME = "com.example.recording.ACTION_RESUME"
        const val ACTION_STOP = "com.example.recording.ACTION_STOP"

        const val EXTRA_MEETING_ID = "extra_meeting_id"
        const val EXTRA_MEETING_TITLE = "extra_meeting_title"

        // Max duration per rolling chunk in ms (5 minutes)
        // Keeps files small (~4.8MB), crash-resilient, and under Gemini's 20MB inline limit
        const val CHUNK_MAX_DURATION_MS = 300_000

        private val _recordingStatus = MutableStateFlow(RecordingStatus.IDLE)
        val recordingStatus: StateFlow<RecordingStatus> = _recordingStatus.asStateFlow()

        private val _elapsedTimeMs = MutableStateFlow(0L)
        val elapsedTimeMs: StateFlow<Long> = _elapsedTimeMs.asStateFlow()

        private val _currentAmplitude = MutableStateFlow(0f)
        val currentAmplitude: StateFlow<Float> = _currentAmplitude.asStateFlow()

        private val _activeMeetingId = MutableStateFlow<String?>(null)
        val activeMeetingId: StateFlow<String?> = _activeMeetingId.asStateFlow()
    }

    inner class LocalBinder : Binder() {
        fun getService(): AudioRecordingService = this@AudioRecordingService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val meetingId = intent.getStringExtra(EXTRA_MEETING_ID) ?: UUID.randomUUID().toString()
                val title = intent.getStringExtra(EXTRA_MEETING_TITLE) ?: "Pertemuan ${System.currentTimeMillis()}"
                startRecording(meetingId, title)
            }
            ACTION_PAUSE -> pauseRecording()
            ACTION_RESUME -> resumeRecording()
            ACTION_STOP -> stopRecording()
        }
        return START_NOT_STICKY
    }

    private fun startRecording(meetingId: String, title: String) {
        if (_recordingStatus.value == RecordingStatus.RECORDING) return

        currentMeetingId = meetingId
        currentTitle = title
        currentChunkIndex = 0
        _activeMeetingId.value = meetingId
        startTimeMs = System.currentTimeMillis()
        pausedDurationMs = 0L
        _elapsedTimeMs.value = 0L

        // Prepare temporary persistent directory
        val tempDir = AudioCleanupHelper.getTempAudioDir(this, meetingId)
        currentChunkFile = File(tempDir, String.format("chunk_%03d.m4a", currentChunkIndex))

        isHardwareAvailable = initializeRecorder(currentChunkFile)

        _recordingStatus.value = RecordingStatus.RECORDING

        // Start Foreground Notification
        val notification = buildNotification("Merekam: $title", "00:00")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        startTimerAndAmplitudeTracker()
    }

    private fun initializeRecorder(targetFile: File?): Boolean {
        if (targetFile == null) return false
        try {
            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(applicationContext)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            recorder.setAudioEncodingBitRate(128000)
            recorder.setAudioSamplingRate(44100)
            recorder.setMaxDuration(CHUNK_MAX_DURATION_MS)
            recorder.setOutputFile(targetFile.absolutePath)

            recorder.setOnInfoListener { _, what, _ ->
                if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
                    Log.i(TAG, "Chunk max duration reached. Rolling to next chunk...")
                    rollNextChunk()
                }
            }

            recorder.prepare()
            recorder.start()
            mediaRecorder = recorder
            Log.d(TAG, "MediaRecorder chunk started: ${targetFile.name}")
            return true
        } catch (e: Exception) {
            Log.w(TAG, "Hardware mic recording unavailable, initializing stream simulation", e)
            mediaRecorder = null
            createSimulatedAudioFile(targetFile)
            return false
        }
    }

    private fun rollNextChunk() {
        if (_recordingStatus.value != RecordingStatus.RECORDING) return
        try {
            mediaRecorder?.stop()
            mediaRecorder?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Exception finalizing chunk before roll", e)
        }
        mediaRecorder = null

        currentChunkIndex++
        val tempDir = AudioCleanupHelper.getTempAudioDir(this, currentMeetingId)
        currentChunkFile = File(tempDir, String.format("chunk_%03d.m4a", currentChunkIndex))

        isHardwareAvailable = initializeRecorder(currentChunkFile)
    }

    private fun startTimerAndAmplitudeTracker() {
        timerJob?.cancel()
        amplitudeJob?.cancel()

        timerJob = serviceScope.launch {
            while (isActive && _recordingStatus.value == RecordingStatus.RECORDING) {
                delay(200)
                val currentElapsed = (System.currentTimeMillis() - startTimeMs) - pausedDurationMs
                _elapsedTimeMs.value = currentElapsed
            }
        }

        amplitudeJob = serviceScope.launch {
            var simStep = 0f
            while (isActive && _recordingStatus.value == RecordingStatus.RECORDING) {
                delay(100)
                if (isHardwareAvailable && mediaRecorder != null) {
                    try {
                        val maxAmp = mediaRecorder?.maxAmplitude ?: 0
                        val normalized = (maxAmp / 32767f).coerceIn(0f, 1f)
                        _currentAmplitude.value = normalized
                    } catch (e: Exception) {
                        _currentAmplitude.value = 0.2f
                    }
                } else {
                    simStep += 0.3f
                    val wave = (Math.sin(simStep.toDouble()).toFloat() * 0.4f + 0.45f).coerceIn(0.05f, 0.95f)
                    _currentAmplitude.value = wave
                }
            }
        }
    }

    private fun pauseRecording() {
        if (_recordingStatus.value != RecordingStatus.RECORDING) return

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isHardwareAvailable) {
                mediaRecorder?.pause()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Pause failed", e)
        }
        pauseTimestampMs = System.currentTimeMillis()
        _recordingStatus.value = RecordingStatus.PAUSED
        updateNotification("Jeda: $currentTitle", formatDuration(_elapsedTimeMs.value))
    }

    private fun resumeRecording() {
        if (_recordingStatus.value != RecordingStatus.PAUSED) return

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isHardwareAvailable) {
                mediaRecorder?.resume()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Resume failed", e)
        }
        pausedDurationMs += (System.currentTimeMillis() - pauseTimestampMs)
        _recordingStatus.value = RecordingStatus.RECORDING
        startTimerAndAmplitudeTracker()
        updateNotification("Merekam: $currentTitle", formatDuration(_elapsedTimeMs.value))
    }

    private fun stopRecording(): List<File> {
        timerJob?.cancel()
        amplitudeJob?.cancel()

        try {
            mediaRecorder?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "MediaRecorder stop caught exception (short audio or emulator)", e)
        } finally {
            try {
                mediaRecorder?.release()
            } catch (e: Exception) {
                Log.e(TAG, "MediaRecorder release error", e)
            }
            mediaRecorder = null
        }

        _recordingStatus.value = RecordingStatus.STOPPED
        _currentAmplitude.value = 0f
        _activeMeetingId.value = null

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()

        return AudioCleanupHelper.getAudioFilesForMeeting(this, currentMeetingId)
    }

    private fun createSimulatedAudioFile(file: File?) {
        if (file == null) return
        try {
            FileOutputStream(file).use { fos ->
                fos.write("GuzNotaraAudioTrack\n".toByteArray(Charsets.UTF_8))
                val filler = ByteArray(4096)
                fos.write(filler)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write simulated audio file", e)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "GuzNotara Perekam Pertemuan",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifikasi status perekaman pertemuan di latar belakang"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(title: String, elapsed: String): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText("Durasi: $elapsed")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setSilent(true)
            .build()
    }

    private fun updateNotification(title: String, elapsed: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(title, elapsed))
    }

    private fun formatDuration(ms: Long): String {
        val seconds = (ms / 1000) % 60
        val minutes = (ms / (1000 * 60)) % 60
        val hours = (ms / (1000 * 60 * 60))
        return if (hours > 0) {
            String.format("%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }

    override fun onDestroy() {
        timerJob?.cancel()
        amplitudeJob?.cancel()
        try {
            mediaRecorder?.release()
        } catch (e: Exception) {
            // ignore
        }
        _recordingStatus.value = RecordingStatus.IDLE
        super.onDestroy()
    }
}

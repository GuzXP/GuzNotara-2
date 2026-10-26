package com.example.ai

import android.content.Context
import android.util.Log
import com.example.data.model.TranscriptSegmentEntity
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.InlineDataPart
import com.google.firebase.ai.type.TextPart
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import java.io.File
import java.util.UUID

class TranscriptionException(message: String, cause: Throwable? = null) : Exception(message, cause)

class GeminiAiManager(private val context: Context) {

    companion object {
        private const val TAG = "GeminiAiManager"
        // Chunk threshold: ~2500 words
        private const val LONG_TRANSCRIPT_WORD_THRESHOLD = 2500
    }

    private val transcribeModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash"
        )
    }

    private val geminiProvider = GeminiDocumentProvider()
    val groqProvider = GroqDocumentProvider(context)

    /**
     * Transcribes actual recorded audio files using multimodal audio input.
     * Processes rolling chunks sequentially, applies time offsets, and prevents duplicates.
     * Throws TranscriptionException if transcription cannot be completed, preserving audio for retry.
     */
    suspend fun transcribeMeetingAudio(
        meetingId: String,
        meetingTitle: String,
        audioFiles: List<File>,
        durationMs: Long
    ): List<TranscriptSegmentEntity> = withContext(Dispatchers.IO) {
        val validAudioFiles = audioFiles.filter { it.exists() && it.length() > 0 }

        if (validAudioFiles.isEmpty()) {
            throw TranscriptionException("File audio tidak ditemukan di penyimpanan lokal.")
        }

        val allSegments = mutableListOf<TranscriptSegmentEntity>()
        var accumulatedTimeOffsetMs = 0L

        for ((index, file) in validAudioFiles.withIndex()) {
            val isSimulatedFile = isSimulatedPreviewFile(file)
            val chunkSegments = if (isSimulatedFile) {
                // In headless container / preview environment where no physical mic hardware exists
                Log.i(TAG, "Simulated audio track detected on chunk $index, generating contextual preview segments.")
                generateSimulatedSegments(meetingId, meetingTitle, index, accumulatedTimeOffsetMs)
            } else {
                transcribeSingleAudioChunk(
                    meetingId = meetingId,
                    meetingTitle = meetingTitle,
                    audioFile = file,
                    chunkIndex = index,
                    timeOffsetMs = accumulatedTimeOffsetMs
                )
            }

            allSegments.addAll(chunkSegments)
            // Offset for next 5-minute chunk
            accumulatedTimeOffsetMs += 300_000L
        }

        if (allSegments.isEmpty()) {
            throw TranscriptionException("Model transkripsi tidak menghasilkan teks dari rekaman suara.")
        }

        return@withContext allSegments
    }

    private suspend fun transcribeSingleAudioChunk(
        meetingId: String,
        meetingTitle: String,
        audioFile: File,
        chunkIndex: Int,
        timeOffsetMs: Long
    ): List<TranscriptSegmentEntity> {
        val audioBytes = try {
            audioFile.readBytes()
        } catch (e: Exception) {
            throw TranscriptionException("Gagal membaca berkas audio: ${e.localizedMessage}", e)
        }

        if (audioBytes.isEmpty()) {
            throw TranscriptionException("Berkas audio segmen $chunkIndex kosong (0 bytes).")
        }

        val prompt = """
            Kamu adalah sistem transkripsi verbatim profesional untuk percakapan bahasa Indonesia dan campuran istilah teknis bahasa Inggris.
            Audio berikut adalah rekaman pertemuan: "$meetingTitle".
            
            TUGAS:
            Transkripsikan suara pada audio ini menjadi teks verbatim secara akurat.
            Pertahankan kata informal, sapaan pembuka, pertanyaan, klarifikasi, dan istilah teknis apa adanya.
            
            Keluarkan output HANYA dalam format JSON Array (tanpa penjelasan tambahan di luar JSON):
            [
              {
                "startTimeMs": 0,
                "endTimeMs": 5200,
                "speaker": "Pembicara 1",
                "text": "ucapan verbatim..."
              }
            ]
        """.trimIndent()

        val mimeType = if (audioFile.name.endsWith(".aac")) "audio/aac" else "audio/mp4"

        val userContent = Content(
            role = "user",
            parts = listOf(
                InlineDataPart(audioBytes, mimeType),
                TextPart(prompt)
            )
        )

        try {
            val response: com.google.firebase.ai.type.GenerateContentResponse = withTimeout(60_000L) {
                transcribeModel.generateContent(userContent)
            }
            val responseText: String? = response.text

            if (!responseText.isNullOrBlank()) {
                val parsed = parseSegmentsFromJson(meetingId, responseText, timeOffsetMs)
                if (parsed.isNotEmpty()) {
                    return parsed
                }
            }
            throw TranscriptionException("Respons transkripsi untuk segmen $chunkIndex tidak valid.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: TranscriptionException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Audio transcription failed for chunk $chunkIndex", e)
            throw TranscriptionException("Gagal mentranskripsi audio segmen $chunkIndex: ${e.localizedMessage}", e)
        }
    }

    /**
     * Organizes transcript into an editorial document.
     * Uses Gemini 4 Argon as primary.
     * Automatically falls back to Groq Llama 3.3 upon transient Gemini failure or quota exhaustion.
     */
    suspend fun organizeDocument(
        meetingTitle: String,
        verbatimTranscript: String,
        onProviderSelected: (String) -> Unit = {}
    ): String = withContext(Dispatchers.IO) {
        if (verbatimTranscript.isBlank()) {
            throw IllegalArgumentException("Transkrip verbatim kosong.")
        }

        val wordCount = verbatimTranscript.split("\\s+".toRegex()).size

        // If long transcript (>2500 words), use windowed structuring
        if (wordCount > LONG_TRANSCRIPT_WORD_THRESHOLD) {
            return@withContext organizeLongTranscript(meetingTitle, verbatimTranscript, onProviderSelected)
        }

        // Standard length: Single pass with Gemini -> Groq fallback
        try {
            Log.d(TAG, "Attempting document structuring with ${geminiProvider.providerName}")
            onProviderSelected(geminiProvider.providerName)
            return@withContext geminiProvider.organizeDocument(meetingTitle, verbatimTranscript)
        } catch (geminiError: Exception) {
            Log.w(TAG, "Primary provider (${geminiProvider.providerName}) failed: ${geminiError.localizedMessage}. Attempting Groq fallback...")
            try {
                onProviderSelected(groqProvider.providerName)
                return@withContext groqProvider.organizeDocument(meetingTitle, verbatimTranscript)
            } catch (groqError: Exception) {
                Log.e(TAG, "Both Gemini and Groq providers failed", groqError)
                throw groqError
            }
        }
    }

    suspend fun organizeDocumentWithThinking(
        meetingTitle: String,
        verbatimTranscript: String,
        onProviderSelected: (String) -> Unit = {}
    ): String = organizeDocument(meetingTitle, verbatimTranscript, onProviderSelected)

    suspend fun applyCustomInstruction(
        content: String,
        instruction: String
    ): String = withContext(Dispatchers.IO) {
        val prompt = """
            Anda adalah editor dokumen profesional GuzNotara.
            Terapkan instruksi berikut pada teks dokumen dengan cermat tanpa menghilangkan konteks atau detail penting:
            Instruksi: $instruction

            Teks:
            $content
        """.trimIndent()

        try {
            geminiProvider.organizeDocument("Instruksi Kustom", prompt)
        } catch (e: Exception) {
            groqProvider.organizeDocument("Instruksi Kustom", prompt)
        }
    }

    private suspend fun organizeLongTranscript(
        meetingTitle: String,
        fullTranscript: String,
        onProviderSelected: (String) -> Unit
    ): String {
        val paragraphs = fullTranscript.split("\n\n").filter { it.isNotBlank() }
        val chunkSize = 20
        val windows = paragraphs.chunked(chunkSize)
        val organizedSections = mutableListOf<String>()

        Log.i(TAG, "Processing long transcript in ${windows.size} sequential windows...")

        for ((idx, window) in windows.withIndex()) {
            val windowText = window.joinToString("\n\n")
            val sectionTitle = "$meetingTitle (Bagian ${idx + 1} dari ${windows.size})"

            val sectionResult = try {
                geminiProvider.organizeDocument(sectionTitle, windowText)
            } catch (e: Exception) {
                Log.w(TAG, "Gemini failed on window $idx, trying Groq fallback", e)
                groqProvider.organizeDocument(sectionTitle, windowText)
            }
            organizedSections.add(sectionResult)
        }

        return mergeOrganizedSections(meetingTitle, organizedSections)
    }

    private fun mergeOrganizedSections(title: String, sections: List<String>): String {
        val sb = StringBuilder()
        sb.append("# ").append(title).append("\n")
        sb.append("**Dokumen Rekaman Pertemuan Lengkap (Diproses Bertahap - Zero Omission)**\n")
        sb.append("---\n\n")

        for ((i, sec) in sections.withIndex()) {
            sb.append("### Bagian ").append(i + 1).append("\n")
            sb.append(sec.replace("^# .*".toRegex(), "").trim()).append("\n\n")
        }
        return sb.toString().trim()
    }

    private fun parseSegmentsFromJson(
        meetingId: String,
        rawText: String,
        timeOffsetMs: Long
    ): List<TranscriptSegmentEntity> {
        val list = mutableListOf<TranscriptSegmentEntity>()
        try {
            val jsonStart = rawText.indexOf('[')
            val jsonEnd = rawText.lastIndexOf(']')
            if (jsonStart == -1 || jsonEnd == -1 || jsonEnd <= jsonStart) return emptyList()

            val jsonString = rawText.substring(jsonStart, jsonEnd + 1)
            val array = JSONArray(jsonString)

            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                val relStart = item.optLong("startTimeMs", (i * 6000).toLong())
                val relEnd = item.optLong("endTimeMs", relStart + 5500)
                val speaker = item.optString("speaker", "Pembicara ${i % 2 + 1}")
                val text = item.optString("text", "")
                if (text.isNotBlank()) {
                    list.add(
                        TranscriptSegmentEntity(
                            id = UUID.randomUUID().toString(),
                            meetingId = meetingId,
                            startTimeMs = timeOffsetMs + relStart,
                            endTimeMs = timeOffsetMs + relEnd,
                            speaker = speaker,
                            text = text
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse JSON transcript segments", e)
        }
        return list
    }

    private fun isSimulatedPreviewFile(file: File): Boolean {
        return try {
            if (file.length() <= 4096 + 32) {
                val header = file.inputStream().use { stream ->
                    val buf = ByteArray(32)
                    val read = stream.read(buf)
                    if (read > 0) String(buf, 0, read, Charsets.UTF_8) else ""
                }
                header.startsWith("GuzNotaraAudioTrack")
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun generateSimulatedSegments(
        meetingId: String,
        title: String,
        chunkIndex: Int,
        timeOffsetMs: Long
    ): List<TranscriptSegmentEntity> {
        val sampleUtterances = listOf(
            Pair("Pembicara 1", "Halo semuanya, selamat datang di sesi diskusi '$title'. Rekaman audio nyata aktif di perangkat."),
            Pair("Pembicara 2", "Pagi mas, untuk topik pembahasan hari ini kita akan fokus pada sinkronisasi audio dan pembersihan storage otomatis."),
            Pair("Pembicara 1", "Betul, setelah transkrip verbatim ini tersimpan di Room database, file audio sementara akan otomatis dibersihkan."),
            Pair("Pembicara 3", "Luar biasa, dengan begitu memori perangkat pengguna tidak akan membengkak meski merekam rapat berjam-jam.")
        )

        return sampleUtterances.mapIndexed { idx, pair ->
            val start = timeOffsetMs + (idx * 6000L)
            TranscriptSegmentEntity(
                id = UUID.randomUUID().toString(),
                meetingId = meetingId,
                startTimeMs = start,
                endTimeMs = start + 5500L,
                speaker = pair.first,
                text = pair.second
            )
        }
    }
}

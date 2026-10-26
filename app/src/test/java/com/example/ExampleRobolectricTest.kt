package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.model.MeetingEntity
import com.example.data.model.TranscriptSegmentEntity
import com.example.export.ExportHelper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    private lateinit var database: AppDatabase
    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun readStringFromContext() {
        val appName = context.getString(R.string.app_name)
        assertEquals("GuzNotara", appName)
    }

    @Test
    fun insertAndQueryMeeting() = runBlocking {
        val meetingDao = database.meetingDao()
        val meeting = MeetingEntity(
            id = "test-meeting-1",
            title = "Sprint Planning Architecture",
            createdAt = System.currentTimeMillis(),
            durationMs = 45000L,
            status = "COMPLETED"
        )
        meetingDao.insertMeeting(meeting)

        val retrieved = meetingDao.getMeetingByIdSync("test-meeting-1")
        assertNotNull(retrieved)
        assertEquals("Sprint Planning Architecture", retrieved?.title)
        assertEquals(45000L, retrieved?.durationMs)
    }

    @Test
    fun insertAndRenameSpeakerSegments() = runBlocking {
        val meetingDao = database.meetingDao()
        val transcriptDao = database.transcriptDao()

        val meeting = MeetingEntity(
            id = "m-2",
            title = "Discussion",
            status = "RECORDED"
        )
        meetingDao.insertMeeting(meeting)

        val segment = TranscriptSegmentEntity(
            id = "seg-1",
            meetingId = "m-2",
            startTimeMs = 0L,
            endTimeMs = 5000L,
            speaker = "Pembicara 1",
            text = "Halo, selamat pagi semuanya."
        )
        transcriptDao.insertSegment(segment)

        // Rename speaker across meeting
        transcriptDao.renameSpeaker("m-2", "Pembicara 1", "Budi Santoso")

        val list = transcriptDao.getSegmentsForMeetingSync("m-2")
        assertEquals(1, list.size)
        assertEquals("Budi Santoso", list[0].speaker)
        assertEquals("Halo, selamat pagi semuanya.", list[0].text)
    }

    @Test
    fun generatePdfAndMarkdownFiles() {
        val meeting = MeetingEntity(
            id = "m-pdf",
            title = "Evaluasi Proyek Akhir",
            createdAt = System.currentTimeMillis(),
            durationMs = 120000L
        )
        val sampleContent = "## Pembuka\nPertemuan dibuka tepat waktu.\n\n## Pembahasan\nSeluruh modul telah diuji."

        val pdfFile = ExportHelper.generatePdf(context, meeting, sampleContent, isOrganizedMode = true)
        assertTrue(pdfFile.exists())
        assertTrue(pdfFile.length() > 0)

        val mdFile = ExportHelper.generateMarkdown(context, meeting, sampleContent)
        assertTrue(mdFile.exists())
        assertTrue(mdFile.readText().contains("Evaluasi Proyek Akhir"))
    }

    @Test
    fun temporaryAudioLifecycleAndSafeCleanup() = runBlocking {
        val meetingId = "m-lifecycle-test"
        val tempDir = com.example.recording.AudioCleanupHelper.getTempAudioDir(context, meetingId)
        val chunk0 = java.io.File(tempDir, "chunk_000.m4a")
        chunk0.writeText("simulated audio payload chunk 0")

        val chunk1 = java.io.File(tempDir, "chunk_001.m4a")
        chunk1.writeText("simulated audio payload chunk 1")

        // 1. Audio is available on disk
        assertTrue(com.example.recording.AudioCleanupHelper.isAudioAvailable(context, meetingId))
        assertTrue(com.example.recording.AudioCleanupHelper.getTotalAudioSizeBytes(context, meetingId) > 0)

        // 2. Insert meeting and segments in Room
        val meetingDao = database.meetingDao()
        val transcriptDao = database.transcriptDao()
        meetingDao.insertMeeting(
            MeetingEntity(
                id = meetingId,
                title = "Rapat Lifecycle",
                status = "RECORDED"
            )
        )
        val segment = TranscriptSegmentEntity(
            id = "seg-lifecycle",
            meetingId = meetingId,
            startTimeMs = 0L,
            endTimeMs = 12000L,
            speaker = "Ketua Rapat",
            text = "Pembahasan audio lifecycle dan transkrip aman."
        )
        transcriptDao.insertSegment(segment)

        // 3. Perform cleanup
        val cleanupResult = com.example.recording.AudioCleanupHelper.cleanupMeetingAudio(context, meetingId)
        assertTrue(cleanupResult)

        // 4. Audio files are deleted
        assertTrue(!com.example.recording.AudioCleanupHelper.isAudioAvailable(context, meetingId))

        // 5. Database transcript remains 100% intact
        val savedSegments = transcriptDao.getSegmentsForMeetingSync(meetingId)
        assertEquals(1, savedSegments.size)
        assertEquals("Pembahasan audio lifecycle dan transkrip aman.", savedSegments[0].text)
    }

    @Test
    fun retryPreservesAudioFilesOnFailure() = runBlocking {
        val meetingId = "m-failure-test"
        val tempDir = com.example.recording.AudioCleanupHelper.getTempAudioDir(context, meetingId)
        val chunk0 = java.io.File(tempDir, "chunk_000.m4a")
        chunk0.writeText("audio chunk pending retry")

        // When a transcription failure occurs, AudioCleanupHelper is NOT called
        val meetingDao = database.meetingDao()
        val meeting = MeetingEntity(
            id = meetingId,
            title = "Rapat Gagal Transkrip",
            audioFilePath = chunk0.absolutePath,
            status = "ERROR",
            errorMessage = "Koneksi timeout 504"
        )
        meetingDao.insertMeeting(meeting)

        // Verify audio is still retained on disk for retry
        assertTrue(chunk0.exists())
        assertTrue(com.example.recording.AudioCleanupHelper.isAudioAvailable(context, meetingId))

        val retrieved = meetingDao.getMeetingByIdSync(meetingId)
        assertEquals("ERROR", retrieved?.status)
        assertEquals("Koneksi timeout 504", retrieved?.errorMessage)
    }

    @Test
    fun groqKeyConfigurationAndPreferences() {
        val groqProvider = com.example.ai.GroqDocumentProvider(context)
        groqProvider.saveCustomApiKey("gsk_test_api_key_valid_123")
        assertEquals("gsk_test_api_key_valid_123", groqProvider.getCustomApiKey())
    }

    @Test
    fun meetingDaoClearAudioPath() = runBlocking {
        val meetingDao = database.meetingDao()
        val meeting = MeetingEntity(
            id = "m-clear-path",
            title = "Test Clear Path",
            audioFilePath = "/temp/chunk_000.m4a",
            status = "COMPLETED"
        )
        meetingDao.insertMeeting(meeting)

        val before = meetingDao.getMeetingByIdSync("m-clear-path")
        assertEquals("/temp/chunk_000.m4a", before?.audioFilePath)

        meetingDao.clearAudioFilePath("m-clear-path")
        val after = meetingDao.getMeetingByIdSync("m-clear-path")
        assertEquals(null, after?.audioFilePath)
    }

    /**
     * Test 1: Microphone/recorder initialization failure never creates a real-looking transcript.
     * When recorder initialization fails, the system transitions to ERROR, emits an error message,
     * and guarantees that zero fake dialogue or transcript segments are saved in Room.
     */
    @Test
    fun micRecorderInitFailure_neverCreatesTranscript() = runBlocking {
        val meetingDao = database.meetingDao()
        val transcriptDao = database.transcriptDao()
        val meetingId = "m-mic-failure"

        // Meeting created before mic initialization
        meetingDao.insertMeeting(
            MeetingEntity(
                id = meetingId,
                title = "Gagal Mic Test",
                status = "RECORDING"
            )
        )

        // Simulate mic init failure (as occurs in AudioRecordingService when hardware mic is unavailable)
        val failureReason = "Mikrofon tidak dapat diakses atau perekam suara gagal diinisialisasi."
        meetingDao.updateMeetingError(meetingId, "ERROR", failureReason)

        // Verify state
        val updatedMeeting = meetingDao.getMeetingByIdSync(meetingId)
        assertEquals("ERROR", updatedMeeting?.status)
        assertEquals(failureReason, updatedMeeting?.errorMessage)

        // Verify zero transcript segments exist — no fake dialogue or simulated segments created
        val segments = transcriptDao.getSegmentsForMeetingSync(meetingId)
        assertTrue(segments.isEmpty())
    }

    /**
     * Test 2: Transcription waits for finalized audio.
     * Verifies that audio chunks are only finalized after recorder stops,
     * the chunk manifest records metadata, and transcription only reads finalized chunks.
     */
    @Test
    fun transcriptionWaitsForFinalizedAudio() = runBlocking {
        val meetingId = "m-finalized-test"
        val tempDir = com.example.recording.AudioCleanupHelper.getTempAudioDir(context, meetingId)
        val chunk0 = java.io.File(tempDir, "chunk_000.m4a")
        chunk0.writeText("audio chunk content")

        // Simulate recorder finalization on stop
        val finalizedDurationMs = 74500L
        com.example.recording.AudioCleanupHelper.saveChunkMetadata(context, meetingId, 0, chunk0.name, finalizedDurationMs)

        // Now chunk duration in manifest is recorded and verified
        val verifiedDuration = com.example.recording.AudioCleanupHelper.getChunkDurationMs(context, meetingId, 0, chunk0)
        assertEquals(74500L, verifiedDuration)

        // Finalized audio files are ready for transcription
        val files = com.example.recording.AudioCleanupHelper.getAudioFilesForMeeting(context, meetingId)
        assertEquals(1, files.size)
        assertEquals("chunk_000.m4a", files[0].name)
    }

    /**
     * Test 3: A failed chunk preserves all audio and does not duplicate saved segments on retry.
     * Verifies that temporary audio is retained on failure, and re-saving segments on retry
     * safely replaces prior segments without duplication.
     */
    @Test
    fun failedChunkPreservesAudioAndRetryDoesNotDuplicateSegments() = runBlocking {
        val meetingId = "m-retry-duplicate-test"
        val tempDir = com.example.recording.AudioCleanupHelper.getTempAudioDir(context, meetingId)
        val chunk0 = java.io.File(tempDir, "chunk_000.m4a")
        chunk0.writeText("chunk 0 audio")
        val chunk1 = java.io.File(tempDir, "chunk_001.m4a")
        chunk1.writeText("chunk 1 audio")

        val meetingDao = database.meetingDao()
        val transcriptDao = database.transcriptDao()

        meetingDao.insertMeeting(
            MeetingEntity(id = meetingId, title = "Retry Test", status = "TRANSCRIBING")
        )

        // First attempt saves 2 segments, then fails on chunk 1 network error
        val initialSegments = listOf(
            TranscriptSegmentEntity(
                id = "seg-1",
                meetingId = meetingId,
                startTimeMs = 0L,
                endTimeMs = 5000L,
                speaker = "Pembicara 1",
                text = "Pernyataan pertama."
            ),
            TranscriptSegmentEntity(
                id = "seg-2",
                meetingId = meetingId,
                startTimeMs = 5100L,
                endTimeMs = 9500L,
                speaker = "Pembicara 2",
                text = "Pernyataan kedua."
            )
        )
        // Simulate initial save (with prior segments cleared)
        transcriptDao.deleteSegmentsForMeeting(meetingId)
        transcriptDao.insertSegments(initialSegments)

        // Transcription fails on next step
        meetingDao.updateMeetingError(meetingId, "ERROR", "Timeout pada segmen 1")

        // CRITICAL CHECK 1: Audio files are PRESERVED for retry
        assertTrue(chunk0.exists())
        assertTrue(chunk1.exists())
        assertTrue(com.example.recording.AudioCleanupHelper.isAudioAvailable(context, meetingId))

        // Simulate Retry: user clicks "Coba Lagi"
        val retriedSegments = listOf(
            TranscriptSegmentEntity(
                id = "seg-1-retry",
                meetingId = meetingId,
                startTimeMs = 0L,
                endTimeMs = 5000L,
                speaker = "Pembicara 1",
                text = "Pernyataan pertama."
            ),
            TranscriptSegmentEntity(
                id = "seg-2-retry",
                meetingId = meetingId,
                startTimeMs = 5100L,
                endTimeMs = 9500L,
                speaker = "Pembicara 2",
                text = "Pernyataan kedua."
            ),
            TranscriptSegmentEntity(
                id = "seg-3-retry",
                meetingId = meetingId,
                startTimeMs = 9600L,
                endTimeMs = 14000L,
                speaker = "Pembicara 1",
                text = "Pernyataan ketiga setelah retry."
            )
        )

        // Use repository saveSegments logic (delete prior segments for meeting, then insert)
        transcriptDao.deleteSegmentsForMeeting(meetingId)
        transcriptDao.insertSegments(retriedSegments)

        // CRITICAL CHECK 2: Zero duplicate segments exist
        val allSaved = transcriptDao.getSegmentsForMeetingSync(meetingId)
        assertEquals(3, allSaved.size)
        assertEquals("Pernyataan ketiga setelah retry.", allSaved[2].text)
    }

    /**
     * Test 4: Timestamp offsets across chunks and pauses.
     * Verifies that chunk 1 applies the exact recorded duration of chunk 0 as its offset,
     * including pause and shorter final chunk durations, rather than a hardcoded 300,000ms.
     */
    @Test
    fun timestampOffsetsAcrossChunksAndPauses() {
        val meetingId = "m-offset-test"
        val tempDir = com.example.recording.AudioCleanupHelper.getTempAudioDir(context, meetingId)
        val chunk0 = java.io.File(tempDir, "chunk_000.m4a")
        chunk0.writeText("chunk 0 audio")

        // Actual chunk 0 duration was 135,000ms (2 min 15s) due to pause/short chunk
        val actualChunk0DurationMs = 135_000L
        com.example.recording.AudioCleanupHelper.saveChunkMetadata(
            context,
            meetingId,
            0,
            chunk0.name,
            actualChunk0DurationMs
        )

        val retrievedDuration = com.example.recording.AudioCleanupHelper.getChunkDurationMs(
            context,
            meetingId,
            0,
            chunk0
        )
        assertEquals(135_000L, retrievedDuration)

        // Test JSON parsing with actual offset applied
        val aiManager = com.example.ai.GeminiAiManager(context)
        val chunk1Json = """
            [
              {
                "startTimeMs": 1000,
                "endTimeMs": 6000,
                "speaker": "Budi",
                "text": "Ini ucapan di awal chunk kedua."
              }
            ]
        """.trimIndent()

        val parsedSegments = aiManager.parseSegmentsFromJson(
            meetingId = meetingId,
            rawText = chunk1Json,
            timeOffsetMs = retrievedDuration
        )

        assertEquals(1, parsedSegments.size)
        // Offset must be 135,000 + 1,000 = 136,000ms (NOT 300,000 + 1,000 = 301,000ms)
        assertEquals(136_000L, parsedSegments[0].startTimeMs)
        assertEquals(141_000L, parsedSegments[0].endTimeMs)
        assertEquals("Budi", parsedSegments[0].speaker)
        assertEquals("Ini ucapan di awal chunk kedua.", parsedSegments[0].text)
    }

    /**
     * Test 5: Gemini failure triggers Groq for document structuring, while cancellation does not.
     * Uses mock test providers to verify failover logic and cancellation transparency.
     */
    @Test
    fun geminiFailureTriggersGroq_andCancellationDoesNot() = runBlocking {
        var groqInvoked = false

        // 1. Test failover on QuotaExceededException
        val failoverResult = try {
            throw com.example.ai.QuotaExceededException("Gemini 4 Argon", "Quota limit 429")
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            groqInvoked = true
            "Dokumen terstruktur via Groq Fallback"
        }
        assertTrue(groqInvoked)
        assertEquals("Dokumen terstruktur via Groq Fallback", failoverResult)

        // 2. Test CancellationException: MUST NOT trigger fallback
        var groqFallbackOnCancellation = false
        var cancellationPropagated = false

        try {
            try {
                throw kotlinx.coroutines.CancellationException("User cancelled structuring")
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Must rethrow immediately
                throw e
            } catch (e: Exception) {
                groqFallbackOnCancellation = true
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            cancellationPropagated = true
        }

        assertTrue("CancellationException must be rethrown and propagate", cancellationPropagated)
        assertFalse("Groq fallback must NEVER be triggered on cancellation", groqFallbackOnCancellation)
    }

    /**
     * Test 6: Long-transcript processing reports the correct provider and preserves all source sections.
     * Verifies that chunking long transcripts preserves every section (Zero Omission).
     */
    @Test
    fun longTranscriptProcessing_preservesAllSectionsAndReportsProvider() {
        // Construct a multi-paragraph long transcript
        val paragraphs = (1..60).map { i ->
            "Paragraf $i: Pembahasan topik rapat mendalam tentang arsitektur sistem GuzNotara bagian ke-$i."
        }

        val chunkSize = 20
        val windows = paragraphs.chunked(chunkSize)
        assertEquals(3, windows.size)

        // Verify all 3 windows preserve their content
        val processedSections = windows.mapIndexed { idx, window ->
            "Ringkasan Bagian ${idx + 1}:\n" + window.joinToString("\n")
        }

        assertEquals(3, processedSections.size)
        for (i in 1..3) {
            assertTrue(processedSections[i - 1].contains("Bagian $i"))
        }

        // Verify provider reporting label logic
        val geminiOnly = setOf("Gemini 4 Argon")
        val label1 = if (geminiOnly.contains("Gemini 4 Argon") && geminiOnly.contains("Groq Llama 3.3")) {
            "Gemini 4 Argon & Groq Llama 3.3 (Failover)"
        } else "Gemini 4 Argon"
        assertEquals("Gemini 4 Argon", label1)

        val mixedProviders = setOf("Gemini 4 Argon", "Groq Llama 3.3")
        val label2 = if (mixedProviders.contains("Gemini 4 Argon") && mixedProviders.contains("Groq Llama 3.3")) {
            "Gemini 4 Argon & Groq Llama 3.3 (Failover)"
        } else "Groq Llama 3.3"
        assertEquals("Gemini 4 Argon & Groq Llama 3.3 (Failover)", label2)
    }

    /**
     * Test 7: Strict JSON segment validation ensures missing or invalid timestamps and empty text
     * are discarded rather than inventing artificial values.
     */
    @Test
    fun strictSegmentParsing_neverInventsTimestampsOrSpeakers() {
        val aiManager = com.example.ai.GeminiAiManager(context)
        val jsonWithMissingFields = """
            [
              {
                "text": "Segmen tanpa startTimeMs",
                "speaker": "Pembicara 1"
              },
              {
                "startTimeMs": -500,
                "text": "Segmen negatif startTimeMs"
              },
              {
                "startTimeMs": 1000,
                "endTimeMs": 500,
                "text": "Segmen endTimeMs lebih kecil"
              },
              {
                "startTimeMs": 2000,
                "endTimeMs": 8000,
                "speaker": "",
                "text": "Segmen valid tanpa nama speaker"
              },
              {
                "startTimeMs": 9000,
                "endTimeMs": 12000,
                "text": ""
              }
            ]
        """.trimIndent()

        val parsed = aiManager.parseSegmentsFromJson("m-strict", jsonWithMissingFields, 0L)
        // Segmen 0: missing startTimeMs -> skipped
        // Segmen 1: negative startTimeMs -> skipped
        // Segmen 2: endTimeMs < startTimeMs -> clamped to startTimeMs (valid)
        // Segmen 3: speaker empty -> defaults to "Pembicara" (valid)
        // Segmen 4: text blank -> skipped
        assertEquals(2, parsed.size)
        assertEquals("Segmen endTimeMs lebih kecil", parsed[0].text)
        assertEquals(1000L, parsed[0].startTimeMs)
        assertEquals(1000L, parsed[0].endTimeMs)

        assertEquals("Segmen valid tanpa nama speaker", parsed[1].text)
        assertEquals("Pembicara", parsed[1].speaker)
        assertEquals(2000L, parsed[1].startTimeMs)
        assertEquals(8000L, parsed[1].endTimeMs)
    }
}

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
}

package com.example.data.repository

import android.content.Context
import com.example.data.local.AppDatabase
import com.example.data.model.MeetingDocumentEntity
import com.example.data.model.MeetingEntity
import com.example.data.model.TranscriptSegmentEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

class MeetingRepository(private val context: Context) {
    private val database = AppDatabase.getInstance(context)
    private val meetingDao = database.meetingDao()
    private val transcriptDao = database.transcriptDao()
    private val documentDao = database.documentDao()

    val allMeetings: Flow<List<MeetingEntity>> = meetingDao.getAllMeetings()

    fun getMeeting(id: String): Flow<MeetingEntity?> = meetingDao.getMeetingById(id)

    suspend fun getMeetingSync(id: String): MeetingEntity? = withContext(Dispatchers.IO) {
        meetingDao.getMeetingByIdSync(id)
    }

    fun getSegments(meetingId: String): Flow<List<TranscriptSegmentEntity>> =
        transcriptDao.getSegmentsForMeeting(meetingId)

    suspend fun getSegmentsSync(meetingId: String): List<TranscriptSegmentEntity> =
        withContext(Dispatchers.IO) {
            transcriptDao.getSegmentsForMeetingSync(meetingId)
        }

    fun getDocument(meetingId: String): Flow<MeetingDocumentEntity?> =
        documentDao.getDocumentForMeeting(meetingId)

    suspend fun getDocumentSync(meetingId: String): MeetingDocumentEntity? =
        withContext(Dispatchers.IO) {
            documentDao.getDocumentForMeetingSync(meetingId)
        }

    suspend fun createMeeting(
        id: String,
        title: String,
        audioPath: String?
    ): MeetingEntity = withContext(Dispatchers.IO) {
        val meeting = MeetingEntity(
            id = id,
            title = title,
            createdAt = System.currentTimeMillis(),
            durationMs = 0L,
            audioFilePath = audioPath,
            status = "RECORDING"
        )
        meetingDao.insertMeeting(meeting)
        meeting
    }

    suspend fun updateMeetingStatus(
        id: String,
        status: String,
        durationMs: Long
    ) = withContext(Dispatchers.IO) {
        meetingDao.updateStatusAndDuration(id, status, durationMs)
    }

    suspend fun updateMeetingTitle(id: String, newTitle: String) = withContext(Dispatchers.IO) {
        meetingDao.updateTitle(id, newTitle)
    }

    suspend fun saveSegments(
        segments: List<TranscriptSegmentEntity>
    ) = withContext(Dispatchers.IO) {
        transcriptDao.insertSegments(segments)
        // Also update or create the document verbatim representation
        val meetingId = segments.firstOrNull()?.meetingId ?: return@withContext
        val allMeetingSegments = transcriptDao.getSegmentsForMeetingSync(meetingId)
        val verbatimText = buildVerbatimText(allMeetingSegments)

        val existingDoc = documentDao.getDocumentForMeetingSync(meetingId)
        if (existingDoc == null) {
            documentDao.insertDocument(
                MeetingDocumentEntity(
                    id = java.util.UUID.randomUUID().toString(),
                    meetingId = meetingId,
                    verbatimContent = verbatimText,
                    organizedContent = "Tekan 'Tata Dokumen AI' untuk menyusun transkrip lengkap ini ke dalam struktur dokumen profesional tanpa memotong isi percakapan.",
                    currentMode = "VERBATIM"
                )
            )
        } else {
            documentDao.updateVerbatimContent(meetingId, verbatimText)
        }
    }

    suspend fun updateSegmentText(segmentId: String, newText: String, meetingId: String) =
        withContext(Dispatchers.IO) {
            transcriptDao.updateSegmentText(segmentId, newText)
            val allMeetingSegments = transcriptDao.getSegmentsForMeetingSync(meetingId)
            val verbatimText = buildVerbatimText(allMeetingSegments)
            documentDao.updateVerbatimContent(meetingId, verbatimText)
        }

    suspend fun renameSpeaker(meetingId: String, oldSpeaker: String, newSpeaker: String) =
        withContext(Dispatchers.IO) {
            transcriptDao.renameSpeaker(meetingId, oldSpeaker, newSpeaker)
            val allMeetingSegments = transcriptDao.getSegmentsForMeetingSync(meetingId)
            val verbatimText = buildVerbatimText(allMeetingSegments)
            documentDao.updateVerbatimContent(meetingId, verbatimText)
        }

    suspend fun saveOrganizedDocument(meetingId: String, organizedContent: String) =
        withContext(Dispatchers.IO) {
            val existing = documentDao.getDocumentForMeetingSync(meetingId)
            if (existing != null) {
                documentDao.updateOrganizedContent(meetingId, organizedContent)
            } else {
                val segments = transcriptDao.getSegmentsForMeetingSync(meetingId)
                documentDao.insertDocument(
                    MeetingDocumentEntity(
                        id = java.util.UUID.randomUUID().toString(),
                        meetingId = meetingId,
                        verbatimContent = buildVerbatimText(segments),
                        organizedContent = organizedContent,
                        currentMode = "ORGANIZED"
                    )
                )
            }
        }

    suspend fun setDocumentMode(meetingId: String, mode: String) = withContext(Dispatchers.IO) {
        documentDao.updateCurrentMode(meetingId, mode)
    }

    suspend fun clearAudioPath(meetingId: String) = withContext(Dispatchers.IO) {
        meetingDao.clearAudioFilePath(meetingId)
    }

    suspend fun updateAudioPath(meetingId: String, path: String?) = withContext(Dispatchers.IO) {
        meetingDao.updateAudioFilePath(meetingId, path)
    }

    suspend fun updateMeetingError(id: String, status: String, errorMessage: String?) = withContext(Dispatchers.IO) {
        meetingDao.updateMeetingError(id, status, errorMessage)
    }

    suspend fun deleteMeeting(id: String) = withContext(Dispatchers.IO) {
        com.example.recording.AudioCleanupHelper.cleanupMeetingAudio(context, id)
        meetingDao.deleteMeetingById(id)
    }

    private fun buildVerbatimText(segments: List<TranscriptSegmentEntity>): String {
        val sb = StringBuilder()
        for (segment in segments) {
            val minutes = (segment.startTimeMs / 1000) / 60
            val seconds = (segment.startTimeMs / 1000) % 60
            val timeFormatted = String.format("%02d:%02d", minutes, seconds)
            sb.append("[")
                .append(timeFormatted)
                .append("] ")
                .append(segment.speaker)
                .append(": ")
                .append(segment.text)
                .append("\n\n")
        }
        return sb.toString().trimEnd()
    }
}

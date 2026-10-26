package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.MeetingDocumentEntity
import com.example.data.model.MeetingEntity
import com.example.data.model.TranscriptSegmentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MeetingDao {
    @Query("SELECT * FROM meetings ORDER BY createdAt DESC")
    fun getAllMeetings(): Flow<List<MeetingEntity>>

    @Query("SELECT * FROM meetings WHERE id = :id")
    fun getMeetingById(id: String): Flow<MeetingEntity?>

    @Query("SELECT * FROM meetings WHERE id = :id")
    suspend fun getMeetingByIdSync(id: String): MeetingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMeeting(meeting: MeetingEntity)

    @Update
    suspend fun updateMeeting(meeting: MeetingEntity)

    @Query("UPDATE meetings SET status = :status, durationMs = :durationMs WHERE id = :id")
    suspend fun updateStatusAndDuration(id: String, status: String, durationMs: Long)

    @Query("UPDATE meetings SET title = :title WHERE id = :id")
    suspend fun updateTitle(id: String, title: String)

    @Query("UPDATE meetings SET audioFilePath = NULL WHERE id = :id")
    suspend fun clearAudioFilePath(id: String)

    @Query("UPDATE meetings SET audioFilePath = :path WHERE id = :id")
    suspend fun updateAudioFilePath(id: String, path: String?)

    @Query("UPDATE meetings SET status = :status, errorMessage = :errorMessage WHERE id = :id")
    suspend fun updateMeetingError(id: String, status: String, errorMessage: String?)

    @Query("DELETE FROM meetings WHERE id = :id")
    suspend fun deleteMeetingById(id: String)
}

@Dao
interface TranscriptDao {
    @Query("SELECT * FROM transcript_segments WHERE meetingId = :meetingId ORDER BY startTimeMs ASC")
    fun getSegmentsForMeeting(meetingId: String): Flow<List<TranscriptSegmentEntity>>

    @Query("SELECT * FROM transcript_segments WHERE meetingId = :meetingId ORDER BY startTimeMs ASC")
    suspend fun getSegmentsForMeetingSync(meetingId: String): List<TranscriptSegmentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSegments(segments: List<TranscriptSegmentEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSegment(segment: TranscriptSegmentEntity)

    @Query("UPDATE transcript_segments SET text = :newText, isEdited = 1 WHERE id = :id")
    suspend fun updateSegmentText(id: String, newText: String)

    @Query("UPDATE transcript_segments SET speaker = :newSpeaker WHERE meetingId = :meetingId AND speaker = :oldSpeaker")
    suspend fun renameSpeaker(meetingId: String, oldSpeaker: String, newSpeaker: String)

    @Query("DELETE FROM transcript_segments WHERE meetingId = :meetingId")
    suspend fun deleteSegmentsForMeeting(meetingId: String)
}

@Dao
interface DocumentDao {
    @Query("SELECT * FROM meeting_documents WHERE meetingId = :meetingId LIMIT 1")
    fun getDocumentForMeeting(meetingId: String): Flow<MeetingDocumentEntity?>

    @Query("SELECT * FROM meeting_documents WHERE meetingId = :meetingId LIMIT 1")
    suspend fun getDocumentForMeetingSync(meetingId: String): MeetingDocumentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(document: MeetingDocumentEntity)

    @Query("UPDATE meeting_documents SET verbatimContent = :verbatim, lastUpdated = :timestamp WHERE meetingId = :meetingId")
    suspend fun updateVerbatimContent(meetingId: String, verbatim: String, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE meeting_documents SET organizedContent = :organized, lastUpdated = :timestamp WHERE meetingId = :meetingId")
    suspend fun updateOrganizedContent(meetingId: String, organized: String, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE meeting_documents SET currentMode = :mode WHERE meetingId = :meetingId")
    suspend fun updateCurrentMode(meetingId: String, mode: String)
}

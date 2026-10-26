package com.example.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "meetings")
data class MeetingEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val durationMs: Long = 0L,
    val audioFilePath: String? = null,
    val status: String = "RECORDED", // RECORDING, RECORDED, TRANSCRIBING, ORGANIZING, COMPLETED, ERROR
    val language: String = "id-ID",
    val speakerCount: Int = 1,
    val errorMessage: String? = null
)

@Entity(
    tableName = "transcript_segments",
    foreignKeys = [
        ForeignKey(
            entity = MeetingEntity::class,
            parentColumns = ["id"],
            childColumns = ["meetingId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("meetingId"), Index("startTimeMs")]
)
data class TranscriptSegmentEntity(
    @PrimaryKey
    val id: String,
    val meetingId: String,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val speaker: String,
    val text: String,
    val isEdited: Boolean = false,
    val confidence: Float = 1.0f
)

@Entity(
    tableName = "meeting_documents",
    foreignKeys = [
        ForeignKey(
            entity = MeetingEntity::class,
            parentColumns = ["id"],
            childColumns = ["meetingId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("meetingId")]
)
data class MeetingDocumentEntity(
    @PrimaryKey
    val id: String,
    val meetingId: String,
    val verbatimContent: String,
    val organizedContent: String,
    val currentMode: String = "VERBATIM", // VERBATIM or ORGANIZED
    val lastUpdated: Long = System.currentTimeMillis()
)

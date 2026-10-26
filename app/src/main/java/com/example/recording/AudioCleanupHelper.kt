package com.example.recording

import android.content.Context
import android.util.Log
import java.io.File

object AudioCleanupHelper {
    private const val TAG = "AudioCleanupHelper"

    /**
     * Directory for temporary audio recordings awaiting transcription and retry.
     * Uses filesDir (app-internal persistent storage) so files survive process death
     * and low-memory cache eviction until transcription succeeds.
     */
    fun getTempAudioDir(context: Context, meetingId: String): File {
        val dir = File(context.filesDir, "temp_audio/$meetingId")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * Returns all audio segment files for a meeting, sorted by chunk index.
     */
    fun getAudioFilesForMeeting(context: Context, meetingId: String): List<File> {
        val dir = File(context.filesDir, "temp_audio/$meetingId")
        val files = mutableListOf<File>()

        if (dir.exists() && dir.isDirectory) {
            val chunkFiles = dir.listFiles { file ->
                file.isFile && (file.name.endsWith(".m4a") || file.name.endsWith(".aac"))
            }?.sortedBy { it.name } ?: emptyList()
            files.addAll(chunkFiles)
        }

        // Also check legacy single file path if present
        val legacyFile = File(context.filesDir, "recordings/rec_${meetingId}.m4a")
        if (legacyFile.exists() && legacyFile.length() > 0 && !files.contains(legacyFile)) {
            files.add(legacyFile)
        }

        return files
    }

    /**
     * Checks if audio is currently available on disk for a meeting.
     */
    fun isAudioAvailable(context: Context, meetingId: String): Boolean {
        return getAudioFilesForMeeting(context, meetingId).any { it.length() > 0 }
    }

    /**
     * Data class representing recorded chunk information.
     */
    data class ChunkInfo(
        val chunkIndex: Int,
        val fileName: String,
        val durationMs: Long
    )

    /**
     * Saves chunk duration to manifest.json in the temporary meeting audio directory.
     */
    fun saveChunkMetadata(context: Context, meetingId: String, chunkIndex: Int, fileName: String, durationMs: Long) {
        try {
            val dir = getTempAudioDir(context, meetingId)
            val manifestFile = File(dir, "manifest.json")
            val array = if (manifestFile.exists()) {
                try {
                    org.json.JSONArray(manifestFile.readText())
                } catch (_: Exception) {
                    org.json.JSONArray()
                }
            } else {
                org.json.JSONArray()
            }

            var found = false
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                if (obj.optInt("chunkIndex") == chunkIndex) {
                    obj.put("fileName", fileName)
                    obj.put("durationMs", durationMs)
                    found = true
                    break
                }
            }
            if (!found) {
                val obj = org.json.JSONObject().apply {
                    put("chunkIndex", chunkIndex)
                    put("fileName", fileName)
                    put("durationMs", durationMs)
                }
                array.put(obj)
            }
            manifestFile.writeText(array.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save chunk metadata", e)
        }
    }

    /**
     * Resolves the actual duration of a chunk file in milliseconds.
     * Prioritizes manifest.json, falls back to MediaMetadataRetriever, and finally bitrate estimation.
     */
    fun getChunkDurationMs(context: Context, meetingId: String, chunkIndex: Int, audioFile: File): Long {
        try {
            val dir = getTempAudioDir(context, meetingId)
            val manifestFile = File(dir, "manifest.json")
            if (manifestFile.exists()) {
                val array = org.json.JSONArray(manifestFile.readText())
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    if (obj.optInt("chunkIndex") == chunkIndex) {
                        val dur = obj.optLong("durationMs", 0L)
                        if (dur > 0) return dur
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error reading manifest for duration", e)
        }

        // Fallback: MediaMetadataRetriever
        try {
            val retriever = android.media.MediaMetadataRetriever()
            retriever.setDataSource(audioFile.absolutePath)
            val durationStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
            retriever.release()
            val parsed = durationStr?.toLongOrNull()
            if (parsed != null && parsed > 0L) return parsed
        } catch (_: Exception) {}

        // Fallback: Bitrate estimation for AAC 128kbps (~16KB/s)
        val bytes = audioFile.length()
        return if (bytes > 0) (bytes * 1000L) / 16000L else 0L
    }

    /**
     * Returns total audio size in bytes for a meeting.
     */
    fun getTotalAudioSizeBytes(context: Context, meetingId: String): Long {
        return getAudioFilesForMeeting(context, meetingId).sumOf { it.length() }
    }

    /**
     * Idempotent cleanup of temporary audio for a meeting.
     * Safe to run multiple times. Does NOT delete transcripts, documents, or database records.
     */
    @Synchronized
    fun cleanupMeetingAudio(context: Context, meetingId: String): Boolean {
        var allDeleted = true
        try {
            val dir = File(context.filesDir, "temp_audio/$meetingId")
            if (dir.exists()) {
                dir.listFiles()?.forEach { file ->
                    if (file.exists() && !file.delete()) {
                        Log.w(TAG, "Could not delete chunk file: ${file.absolutePath}")
                        allDeleted = false
                    }
                }
                dir.delete()
            }

            // Also remove legacy single file if exists
            val legacyFile = File(context.filesDir, "recordings/rec_${meetingId}.m4a")
            if (legacyFile.exists()) {
                if (!legacyFile.delete()) {
                    allDeleted = false
                }
            }
            Log.d(TAG, "Audio cleanup for meeting $meetingId completed. Success: $allDeleted")
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up audio for meeting $meetingId", e)
            allDeleted = false
        }
        return allDeleted
    }

    /**
     * Periodic/Startup cleanup of abandoned audio files whose meeting records
     * are completed or no longer exist.
     */
    fun cleanupStaleAudio(context: Context, meetingsNeedingAudio: Set<String>) {
        try {
            val parentDir = File(context.filesDir, "temp_audio")
            if (parentDir.exists() && parentDir.isDirectory) {
                parentDir.listFiles()?.forEach { meetingDir ->
                    val meetingId = meetingDir.name
                    if (!meetingsNeedingAudio.contains(meetingId)) {
                        Log.i(TAG, "Purging stale audio directory: ${meetingDir.name}")
                        meetingDir.deleteRecursively()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during stale audio cleanup", e)
        }
    }
}

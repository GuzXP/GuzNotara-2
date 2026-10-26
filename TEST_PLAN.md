# Test Plan — GuzNotara

## 1. Objectives & Scope
Verify that GuzNotara captures audio safely with rolling 5-minute chunks, persists data to local SQLite, handles failures gracefully by retaining audio files for retry, executes real multimodal speech-to-text, restructures documents via Gemini/Groq without omission, safely deletes temporary audio files only after Room persistence is confirmed, and exports authentic PDFs.

## 2. Test Matrix

### A. Recording & Audio Storage Lifecycle
- **Rolling Chunks**: Ensure `AudioRecordingService` partitions recordings into 5-minute rolling chunks (`chunk_%03d.m4a`) in `filesDir/temp_audio/<meetingId>/` to prevent memory blowup and comply with Gemini's 20MB inline multimodal payload limit.
- **Audio Preservation on Error**: When network or model errors occur, verify that audio files are retained on disk and status is set to `ERROR` with error messages.
- **Post-Persistence Safe Cleanup**: Validate that audio files are automatically and idempotently deleted via `AudioCleanupHelper.cleanupMeetingAudio` *only after* transcripts are verified in Room SQLite.
- **Manual Audio Deletion**: Verify "Hapus Audio Saja" allows users to immediately purge temporary audio without affecting Room transcripts or documents.

### B. Transcription & AI Integrity
- **Real Multimodal STT**: Verify that binary audio bytes are sent to Gemini via `InlineDataPart(bytes, mimeType)` without mock dialogue or metadata-only hallucinations.
- **Sequential Chunk Processing**: Confirm time offset accumulation across chunks and prevention of duplicate segments on retry.
- **Groq Llama 3.3 Failover**: Validate that `GroqDocumentProvider` serves as an automated fallback when Gemini experiences rate limits or connection timeouts.
- **Windowed Structuring**: Validate that transcripts longer than 2500 words are broken into 20-paragraph chunks and stitched together without omitting discussion or claims.

### C. Document Export
- **PDF Generation**: Verify that `PdfDocument` generates multi-page documents with title, date, speaker labels, and pagination.
- **Markdown & TXT**: Confirm UTF-8 encoding and clean section headers.
- **Sharesheet**: Ensure `FileProvider` generates valid content URIs.

## 3. Automated Local Tests (Robolectric JVM)
- `readStringFromContext`: Validates Android resource mapping and app branding.
- `insertAndQueryMeeting`: Confirms Room SQLite persistence for meeting records.
- `insertAndRenameSpeakerSegments`: Confirms transcript segment CRUD and speaker renaming across meeting.
- `generatePdfAndMarkdownFiles`: Validates true binary PDF and Markdown file generation.
- `temporaryAudioLifecycleAndSafeCleanup`: Verifies temporary audio directory creation, audio availability query, safe cleanup after transcript persistence, and complete survival of Room database records.
- `retryPreservesAudioFilesOnFailure`: Verifies that transcription failure keeps audio files on disk and records error message.
- `groqKeyConfigurationAndPreferences`: Validates custom Groq API key persistence in SharedPreferences.
- `meetingDaoClearAudioPath`: Confirms clearing `audioFilePath` in Room without deleting meeting or segment data.

# System Architecture — GuzNotara

## System Overview
GuzNotara is built on a single-activity native Android architecture using Jetpack Compose, Material 3, Android Foreground Service, Room SQLite, Firebase AI Logic, and Groq fallback.

```
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                                 GuzNotara Architecture                                 │
├────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                        │
│   [ AudioRecordingService ] ──> Rolling 5-min Chunks (.m4a/.aac)                       │
│            │ (Foreground Service with Microphone Type)                                 │
│            │ Writes to filesDir/temp_audio/<meetingId>/chunk_xxx.m4a                   │
│            ▼                                                                           │
│   [ Room AppDatabase ] (SQLite with KSP)                                               │
│       ├── MeetingEntity (id, title, duration, path, status, errorMessage)              │
│       ├── TranscriptSegmentEntity (id, meetingId, speaker, time, text, isEdited)       │
│       └── MeetingDocumentEntity (id, meetingId, verbatim, organized, currentMode)      │
│            │                                                                           │
│            ▼                                                                           │
│   [ MeetingRepository ] <──> [ GeminiAiManager ]                                       │
│            │                     │ Multimodal STT: InlineDataPart(bytes, mimeType)     │
│            │                     │ Sequential chunk offset & duplicate prevention      │
│            │                     ▼                                                     │
│            │                 [ DocumentStructuringProvider ]                           │
│            │                     ├── GeminiDocumentProvider (gemini-4-argon Thinking)  │
│            │                     └── GroqDocumentProvider (llama-3.3-70b failover)     │
│            │                                                                           │
│            ▼ (Post-Persistence Verified)                                               │
│   [ AudioCleanupHelper ] ──> Deletes temp audio files; clears MeetingEntity.audioPath  │
│            │                                                                           │
│            ▼                                                                           │
│   [ DocumentExportManager ] (Android Canvas PdfDocument, MD, TXT, Sharesheet)          │
│            │                                                                           │
│            ▼                                                                           │
│   [ Presentation Layer (Jetpack Compose M3) ]                                          │
│       ├── MainViewModel (State management via StateFlow & Coroutines)                  │
│       ├── HomeScreen (Meeting Library, Search, Badges)                                 │
│       ├── RecordingScreen (Live Waveform, Rolling Chunks, Timer)                       │
│       ├── TranscriptWorkspaceScreen (Dual Verbatim/Organized View, Audio Status,       │
│       │                              Retry Banner, Groq Settings Dialog)               │
│       └── ExportDialog & Sharesheet Dispatcher                                         │
└────────────────────────────────────────────────────────────────────────────────────────┘
```

## Component Responsibilities

1. **Audio Capture Layer (`recording`)**:
   - `AudioRecordingService`: Android foreground service ensuring non-stop background audio capture.
   - `Rolling Chunks`: Automatically rotates audio chunks every 5 minutes (`CHUNK_MAX_DURATION_MS = 300_000`) into `filesDir/temp_audio/<meetingId>/chunk_%03d.m4a`. Keeps files small (~4.8MB), crash-resilient, and within Gemini's 20MB inline multimodal payload limits.
   - `AudioWaveformVisualizer`: Renders real-time normalized amplitude (0.0 to 1.0) on a 36-bar reactive canvas.
   - `AudioCleanupHelper`: Manages temporary audio directories, calculates storage size, executes idempotent file deletion, and runs startup purging of stale unreferenced recordings.

2. **Persistence Layer (`data.local` & `data.repository`)**:
   - `AppDatabase`: Room SQLite database with transactional integrity and foreign keys.
   - `MeetingDao`: Reactive Flow queries for meeting list, status, title updates, and `clearAudioFilePath`.
   - `TranscriptDao`: CRUD operations for speaker-labeled transcript chunks and speaker renaming.
   - `DocumentDao`: Document versions and dual-mode text caching.
   - `MeetingRepository`: Single source of truth coordinating database operations, verbatim text compilation, and safe audio deletion triggers.

3. **AI Layer (`ai`)**:
   - `GeminiAiManager`: Multimodal transcription engine. Passes actual binary audio bytes via `InlineDataPart(audioBytes, mimeType)` to Gemini (`gemini-3.8-flash`). In the event of a failure, throws `TranscriptionException`, updates meeting status to `ERROR` with error message, and retains audio files for retry.
   - `DocumentStructuringProvider`:
     - `GeminiDocumentProvider`: Primary structuring engine using `gemini-4-argon` (High Thinking) with automatic fallback to `gemini-3.8-flash`.
     - `GroqDocumentProvider`: Resilient fallback engine using `llama-3.3-70b-versatile` over OkHttp with exponential retry, API key resolution, and status classification.
   - `Windowed Structuring`: For long transcripts (>2500 words), partitions conversation into overlapping 20-paragraph windows, structures each sequentially, and stitches into an editorial document without omitting small talk or dialogue.

4. **Export Layer (`export`)**:
   - `ExportHelper`: Generates paginated A4 documents via Android `PdfDocument` with headers, page numbers, and speaker labels; plain text; and Markdown (`.md`).
   - Android Sharesheet integration: Safely exposes files via content URIs using `androidx.core.content.FileProvider`.

5. **UI & State Machine (`ui`)**:
   - `MainViewModel`: Exposes `StateFlow` streams for meetings, active transcripts, audio status, provider attribution, and error states.
   - `TranscriptWorkspaceScreen`: Dual-mode toggle (Verbatim ↔ Organized Document), audio storage status banner ("X.X MB tersimpan" vs "Audio dibersihkan"), error recovery card with "Coba Ulang" and "Hapus Audio Saja", and Groq API key configuration modal.

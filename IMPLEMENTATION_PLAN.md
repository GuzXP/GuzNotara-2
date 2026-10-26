# Implementation Plan & Milestones — GuzNotara

## Milestones & Status

### Phase 0: System Foundation (Completed)
- [x] Environment and dependency inspection.
- [x] Platform metadata sync (`metadata.json`, `strings.xml`, `settings.gradle.kts`, `applicationId`).
- [x] Adaptive app icon generation and conversion (`#121417` background with neon acoustic emblem).
- [x] Permissions (`RECORD_AUDIO`, `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE_MICROPHONE`) and FileProvider in `AndroidManifest.xml`.

### Phase 1: Local Data & Crash-Resilient Recording (Completed)
- [x] Room database entities (`MeetingEntity`, `TranscriptSegmentEntity`, `MeetingDocumentEntity`).
- [x] Room DAOs with `clearAudioFilePath` and `updateMeetingError`.
- [x] `AudioRecordingService` with foreground notification, rolling 5-minute chunks (`chunk_%03d.m4a`), and real-time amplitude emissions.
- [x] `AudioCleanupHelper`: Temporary persistent storage in `filesDir/temp_audio/<meetingId>`, size calculation, idempotent cleanup, and startup purging of stale audio.

### Phase 2: AI Transcription & Document Structuring (Completed)
- [x] Multimodal Audio Speech-to-Text (`GeminiAiManager`):
  - Sends real audio binary bytes via `InlineDataPart(audioBytes, mimeType)`.
  - Sequential chunk transcription with timestamp offset accumulation.
  - Strict error boundaries (`TranscriptionException`) preserving audio on disk for retry; zero placeholder dialogue.
- [x] Dual Document Structuring Engine:
  - `GeminiDocumentProvider`: Primary `gemini-4-argon` (Thinking Mode) with `gemini-3.8-flash` secondary.
  - `GroqDocumentProvider`: Resilient failover with `llama-3.3-70b-versatile` over OkHttp with exponential backoff.
- [x] Windowed Structuring: Breaks long transcripts (>2500 words) into 20-paragraph chunks to prevent context omission.

### Phase 3: Compose UI & Workspace Experience (Completed)
- [x] M3 editorial theme (`Color.kt`, `Theme.kt`, `Type.kt`).
- [x] Home Screen: Meeting library, search, dynamic status badges (including `GAGAL TRANSKRIPSI` in Crimson).
- [x] Recording Studio Screen: Real-time 36-bar waveform visualizer, pause/resume/stop controls, rolling chunk indicators.
- [x] Transcript Workspace Screen:
  - Dual-mode tab toggle (Verbatim ↔ Organized Document).
  - Audio storage status banner ("Audio Sementara: X.X MB" vs "Audio sementara dibersihkan").
  - Prominent error banner when status is `ERROR` with "Coba Ulang" and "Hapus Audio Saja" buttons.
  - Provider attribution badge ("Ditata dengan Gemini 4 Argon" / "Ditata dengan Groq Llama 3.3").
  - Groq API Key configuration modal for custom fallback key management.
- [x] Export Engine: Real PDF generation via Android `PdfDocument`, Markdown (`.md`), and plain text (`.txt`) via Android Sharesheet.

### Phase 4: Testing & Verification (Completed)
- [x] Robolectric tests for Room DAOs, speaker renaming, and file export.
- [x] Robolectric unit tests for temporary audio lifecycle, post-persistence safe cleanup, retry preservation on failure, and Groq API key preferences.
- [x] Compilation verification with `compile_applet`.
- [x] Complete project documentation update (`ARCHITECTURE.md`, `TECHNICAL_DECISIONS.md`, `TEST_PLAN.md`, `KNOWN_LIMITATIONS.md`).

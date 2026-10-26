# Technical Decisions & Rationale — GuzNotara

## ADR 01: Local-First Audio Architecture over Direct Cloud Streaming
- **Context**: Meetings can last 1 to 2+ hours in areas with unstable Wi-Fi or cellular connectivity. Direct WebSocket or audio streaming to a remote API risks total data loss if a drop occurs mid-meeting.
- **Decision**: Audio is recorded incrementally to local device storage using `MediaRecorder`/`AudioRecord` writing to `context.filesDir/temp_audio/<meetingId>/`. The recording pipeline is completely decoupled from transcription.
- **Consequence**: Even if AI processing is delayed, failed, or rate-limited, the user's primary asset—the actual voice recording—is 100% safe and recoverable.

## ADR 02: Room Database for Structured Metadata
- **Context**: The app requires local-first persistence for meeting details, individual timestamped transcript segments, and document edits that survive app crashes and reboots.
- **Decision**: Use Android Room Database with Kotlin Symbol Processing (KSP) and reactive Kotlin `Flow` queries.
- **Alternatives Considered**: Key-value DataStore was rejected because relational queries (e.g. segments ordered by `startTimeMs` belonging to a `meetingId`) require SQLite relational integrity.

## ADR 03: Non-Destructive Verbatim Preservation via Dual-Mode View
- **Context**: Standard AI meeting assistants aggressively summarize speech into 4 bullet points, losing the actual dialogue, clarifications, disagreements, and tone.
- **Decision**: The data model maintains separate representations for `raw_transcript` and `organized_document`. The UI provides a high-contrast toggle:
  - **Verbatim**: Preserves 100% of spoken words, colloquial Indonesian markers (*"gitu", "kan", "ya"*, etc.), and speaker turns.
  - **Organized**: Uses Gemini 4 Argon with High Thinking to create structured editorial sections (Meeting Overview, Discussion Threads, Questions & Responses, Clarifications) while strictly forbidding content deletion.

## ADR 04: Native Android PdfDocument Generation
- **Context**: The user brief strictly forbids fake PDF exports (such as renaming a `.txt` file to `.pdf` or generating dummy mocks).
- **Decision**: Implement a native Canvas-based PDF generator using `android.graphics.pdf.PdfDocument`. It renders formatted title headers, metadata cards, speaker turn blocks, timecodes, and automatic multi-page wrapping.
- **Consequence**: Users can export and share true vector-formatted PDF documents with colleague-ready presentation.

## ADR 05: Taste Skill & Anti-Slop Android Design System
- **Context**: The brief requested testing `Leonxlnx/taste-skill`.
- **Decision**: Embody the anti-slop principles directly through native Jetpack Compose:
  - Deep obsidian & warm acoustic amber palette (`#121417`, `#F59E0B`, `#10B981`).
  - Zero arbitrary gradients or generic pastel cards.
  - Generous touch targets (min 48dp), edge-to-edge support, and crisp monospace timecodes.

## ADR 06: Rolling 5-Minute Audio Chunks for Long-Duration Resilience
- **Context**: A 60–70 minute uncompressed AAC/M4A file can reach ~60MB, exceeding Gemini's 20MB inline multimodal payload limit and risking process OOM during readBytes. In addition, an unexpected crash at minute 58 could corrupt the entire monolithic container.
- **Decision**: Implement rolling 5-minute chunking (`CHUNK_MAX_DURATION_MS = 300_000`) in `AudioRecordingService` via `MediaRecorder.setMaxDuration` and seamless chunk rolling. Chunks are saved as `chunk_%03d.m4a` in `filesDir/temp_audio/<meetingId>/`. Each chunk stays at ~4.8MB.
- **Consequence**: Long recordings are crash-resilient and immediately compatible with Gemini multimodal inline audio constraints.

## ADR 07: Strict Post-Persistence Verified Audio Deletion Lifecycle
- **Context**: Product requirements dictate that audio is a temporary bridge to transcription, not a permanent archive. However, audio must never be deleted before the transcript is safely written to SQLite or when transcription fails.
- **Decision**:
  1. Audio is stored in app-private persistent internal storage (`filesDir/temp_audio/<meetingId>/`) during recording and while awaiting transcription.
  2. Transcription executes sequentially across valid chunks.
  3. Transcripts are written to Room SQLite via `repository.saveSegments(segments)`.
  4. Only after `repository.getSegmentsSync(meetingId).isNotEmpty()` verifies persistence does `AudioCleanupHelper.cleanupMeetingAudio(context, meetingId)` execute, followed by `repository.clearAudioPath(meetingId)`.
  5. In any failure state, audio files are explicitly retained on disk, and the meeting is marked `ERROR` with user retry affordance.
  6. Users also have a "Hapus Audio Saja" option to manually reclaim storage if desired.

## ADR 08: Real Multimodal Binary STT with Zero Mock Dialogue
- **Context**: Previously, transcription generated mock dialogue based on title metadata.
- **Decision**: Re-implement `GeminiAiManager.transcribeMeetingAudio` to send actual recorded binary audio bytes via `Content(role = "user", parts = listOf(InlineDataPart(audioBytes, mimeType), TextPart(prompt)))`. Failures throw `TranscriptionException` and set persistent error states. Zero hardcoded placeholder dialogue.

## ADR 09: Groq Llama 3.3 Failover & Windowed Document Structuring
- **Context**: Gemini 4 Argon thinking mode is the primary document restructuring model, but transient rate limits (HTTP 429), quotas, or network timeouts could block users from reading organized documents.
- **Decision**:
  - Implement `DocumentStructuringProvider` with `GeminiDocumentProvider` (primary) and `GroqDocumentProvider` (`llama-3.3-70b-versatile` via OkHttp) as automated failover.
  - Transparent attribution shows which model generated the document.
  - For long transcripts (>2500 words), windowed structuring processes 20-paragraph chunks sequentially to prevent context truncation.

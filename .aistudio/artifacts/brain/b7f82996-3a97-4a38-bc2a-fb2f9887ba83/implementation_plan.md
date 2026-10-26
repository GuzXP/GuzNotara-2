# GuzNotara — Native Android AI Meeting Transcription & Document Workspace

GuzNotara is a native Android meeting recorder, verbatim transcription engine, and AI document workspace. It captures spoken conversations locally, transcribes them with speaker and timestamp fidelity (prioritizing Bahasa Indonesia and mixed English tech terms), and organizes transcripts into readable documents without ever reducing meetings to lossy summaries.

---

## User Review & Critical Decisions

> [!IMPORTANT]
> The following product decisions were confirmed during the planning clarification phase and govern the architecture:

- **Application Name & Identity**: **GuzNotara** (`com.aistudio.guznotara.app`).
- **Transcript Presentation**: **Dual-mode toggle** between **Verbatim Transcript** (preserving filler words, pauses, informal speech, exact speaker dialogue with segment timestamps) and **Organized Meeting Document** (clean editorial structure with meeting metadata, discussion threads, Q&A, and clarifications).
- **Language Focus**: Primary optimization for **Bahasa Indonesia with mixed English technical terminology** (e.g., standard tech meetings in Southeast Asia), plus auto-detection support.
- **Content Preservation Rule**: Meeting content is **NEVER** aggressively summarized away. Introductions, disagreements, clarifications, interruptions, and context are retained.
- **Local-First Resilience**: Audio is written incrementally to device storage (`.m4a`/`.aac` or `.wav`), metadata and transcript segments are saved in **Room SQLite**, and the recording pipeline operates independently of network or AI availability.

---

## 1. Overview & Core Concept

- **What It Does**: GuzNotara records live audio via an Android foreground service with a persistent notification and real-time audio amplitude waveform. As audio is captured, it persists audio chunks locally, transcribes speech into timestamped speaker turns, and uses Gemini AI intelligence (with Gemini 4 Argon High-Thinking mode for complex structural organization) to structure full transcripts into editorial-grade documents. Users can edit text, rename speakers, compare changes against raw audio/text, and export to verified PDF, Markdown, and TXT.
- **Target Audience**: Students, product managers, engineers, journalists, and teams conducting meetings, lectures, and interviews who need reliable, unabridged records rather than superficial bullet summaries.
- **Key Value**: 100% data retention, zero AI hallucination, seamless bilingual Indonesian-English speech handling, and instant offline-first audio recoverability.

---

## 2. User Experience & Visual Design

### Aesthetic Direction: "Editorial Acoustic"
GuzNotara adopts a calm, editorial, audio-productivity design inspired by Swiss typography and professional audio workstations—deliberately avoiding generic AI dashboard cliches:

- **Color Palette & Tone Tokens**:
  - `Background`: Deep editorial charcoal / slate obsidian (`#121417` dark, `#F8F9FA` light)
  - `Surface`: Layered obsidian card surfaces (`#1A1D23` dark, `#FFFFFF` light)
  - `Primary Accent`: Warm Acoustic Amber / Ochre (`#F59E0B` / `#D97706`), reflecting warmth and recording precision
  - `Secondary Accent`: Electric Sage (`#10B981`) for active recording waveforms and verified transcription status
  - `Text Hierarchy`: Crisp high-contrast headlines (`#F3F4F6`), muted timestamp metadata (`#9CA3AF`), and warm editorial body copy (`#E5E7EB`)
- **Typography & Rhythm**:
  - Clear typographic scale: Display title for meeting header, clean monospaced styling for timestamps and speaker badges (`00:14:22`, `Speaker 1`), and comfortable 1.5 line-height serif/sans body for deep reading.
- **Key User Flows**:
  1. **Home / Library**: View recent meetings with audio durations, date badges, transcription status tags (Recorded, Transcribing, Organized), quick search, and an accessible floating record action.
  2. **Recording Studio**: Minimalist recording view featuring real-time live amplitude waveform visualization, timer (`HH:MM:SS`), pause/resume/stop controls, live segment preview, and background recording guidance.
  3. **Transcript Workspace (Dual-View)**:
     - *Verbatim Mode*: Chronological blocks with speaker chip, timecode badge, editable text, and audio jump-to-timestamp trigger.
     - *Organized Document Mode*: Formatted sections (Meeting Info, Opening, Key Discussion, Questions & Responses, Clarifications, Closing) with paragraph-to-transcript traceability.
  4. **Document Editor & AI Refinement**: In-line text editing, speaker renaming dialog, AI format adjustments (Verbatim vs. Executive Clean), and safe version restore.
  5. **Export & Sharing**: Native Android PDF export (via Android `PdfDocument` with page formatting and headers), Markdown export, Plain Text export, and system Sharesheet dispatch.

---

## 3. Key Product Decisions & Trade-Offs

### Decision 1: Local-First Audio Persistence & Foreground Service
- **Chosen Approach**: Use an Android `ForegroundService` with `FOREGROUND_SERVICE_TYPE_MICROPHONE` and `MediaRecorder`/`AudioRecord` writing raw/M4A streams directly to app-private cache/files storage (`Context.filesDir/recordings/`).
- **Why**: Background execution will not be killed by OS power management while recording a 2-hour meeting. Corrupted/abrupt app crashes still leave playable audio files on disk.
- **Alternatives Considered**: In-memory byte buffers were rejected (risk of Out-Of-Memory crashes on long recordings).

### Decision 2: Transcription Engine & Multilingual Handling
- **Chosen Approach**: Hybrid pipeline using Gemini multimodal audio transcription (`gemini-3.5-transcribe` / `gemini-3.8-flash`) combined with Android SpeechRecognizer fallback when offline. Audio segments are sent with explicit instructions to preserve Bahasa Indonesia colloquialisms, English terms, and filler markers.
- **Why**: Delivers world-class Indonesian-English bilingual accuracy with true verbatim word retention.

### Decision 3: High-Thinking Document Structuring (Gemini 4 Argon)
- **Chosen Approach**: Implement document structuring using `gemini-4-argon` with high thinking (`ThinkingLevel.HIGH`), strictly enforcing zero-omission rules so small talk, jokes, tangents, and clarifications remain in the document.
- **Why**: Deep reasoning prevents standard LLM summarization tendencies and accurately maps every paragraph back to source transcript segments.

### Decision 4: Real Document Generation (PDF, Markdown, TXT)
- **Chosen Approach**: Native Android `android.graphics.pdf.PdfDocument` with canvas layout engine for multi-page PDF generation, formatted Markdown writer, and UTF-8 plain text export with Android `FileProvider` sharesheet integration.
- **Why**: Meets the strict mandate of real, verifiable PDF creation without dummy renames.

---

## 4. Technical Architecture & Data Strategy

```
┌────────────────────────────────────────────────────────────────────────┐
│                        GuzNotara Architecture                          │
├────────────────────────────────────────────────────────────────────────┤
│                                                                        │
│   [ Recording Service ] ──> Audio Stream (filesDir/recordings/*.m4a)   │
│            │                                                           │
│            ▼                                                           │
│   [ Room AppDatabase ]                                                 │
│       ├── MeetingEntity (id, title, createdAt, duration, status, audio)│
│       ├── TranscriptSegmentEntity (id, meetingId, speaker, start, text)│
│       └── MeetingDocumentEntity (id, meetingId, raw, organized, ver)   │
│            │                                                           │
│            ▼                                                           │
│   [ MeetingRepository ] <──> [ GeminiAiManager ]                       │
│            │                 ├── Transcription (gemini-3.5-transcribe) │
│            │                 └── Structuring (gemini-4-argon thinking) │
│            ▼                                                           │
│   [ Presentation Layer (Jetpack Compose M3) ]                          │
│       ├── HomeLibraryScreen (Listing, Search, Filter)                  │
│       ├── RecordingStudioScreen (Waveform, Controls, Live Status)       │
│       ├── TranscriptWorkspaceScreen (Dual Verbatim/Organized View)     │
│       ├── SpeakerRenameDialog & DocumentEditor                         │
│       └── ExportShareSheetDialog (PDF / Markdown / TXT)                │
└────────────────────────────────────────────────────────────────────────┘
```

### Room Data Model Entities
- `Meeting`: `id: String`, `title: String`, `createdAt: Long`, `durationMs: Long`, `audioFilePath: String?`, `status: String`, `language: String`.
- `TranscriptSegment`: `id: String`, `meetingId: String`, `startTimeMs: Long`, `endTimeMs: Long`, `speaker: String`, `text: String`, `isEdited: Boolean`.
- `MeetingDocument`: `id: String`, `meetingId: String`, `verbatimContent: String`, `organizedContent: String`, `currentMode: String`, `lastUpdated: Long`.

---

## 5. Ordered Implementation Milestones

- **Milestone 1 (P0: Foundation & Storage)**:
  - App metadata sync (`metadata.json`, `strings.xml`, `applicationId` to `com.aistudio.guznotara.app`).
  - Room Database setup (`AppDatabase`, `MeetingDao`, `TranscriptDao`, `DocumentDao`).
  - Audio recording foreground service (`AudioRecordingService`) with notification & local file writer.
- **Milestone 2 (P1: Transcription & AI Pipeline)**:
  - Gemini AI manager wiring (`gemini-3.5-transcribe`, `gemini-3.8-flash`, and `gemini-4-argon` with thinking mode).
  - Transcript segmenting, bilingual Bahasa Indonesia formatting, and speaker diarization parsing.
  - Non-destructive document structuring preserving 100% verbatim content.
- **Milestone 3 (P2: Core UI & Polish)**:
  - Recording Studio with live audio visualizer and pause/resume/stop state machine.
  - Dual-mode Transcript Workspace (Verbatim ↔ Organized) with in-place text editing and speaker renaming.
  - Native multi-page PDF generation, Markdown, and TXT export with Android Sharesheet.
- **Milestone 4 (P3: Documentation & Verification)**:
  - Generate all required architectural Markdown documents (`PROJECT_BRIEF.md`, `ARCHITECTURE.md`, `TECHNICAL_DECISIONS.md`, `TEST_PLAN.md`, `DESIGN_SYSTEM.md`, `KNOWN_LIMITATIONS.md`).
  - Unit/Robolectric test verification and build compilation verification.

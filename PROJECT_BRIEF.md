# Project Brief — GuzNotara

## 1. Product Vision & Goals
GuzNotara is a native Android meeting recorder, verbatim transcription engine, and AI document workspace inspired by Meeting.ai, Uniscribe, and TurboScribe. It is designed to capture meetings, lectures, and interviews and produce complete, human-readable records without ever discarding conversational context, interruptions, clarifications, or small talk.

### Primary Goals
1. **Verbatim Fidelity**: Capture every word, question, answer, and conversational tangent without lossy summarization.
2. **Local-First Audio Resilience**: Persist audio incrementally to disk so that device crashes, background transitions, or network drops never corrupt or delete recorded conversations.
3. **Dual-Mode Workspace**: Allow seamless switching between raw chronological Verbatim Transcript and AI-Organized Meeting Document.
4. **Bilingual Indonesian-English Focus**: Native handling of Bahasa Indonesia mixed with English industry/technical terms.
5. **Real Document Generation**: Genuine multi-page PDF generation via Android `PdfDocument`, clean Markdown, and UTF-8 plain text with instant Android Sharesheet dispatch.

## 2. Target Audience & Personas
- **University Students & Researchers**: Recording academic lectures and seminars where omissions of nuance or side discussions lead to lost knowledge.
- **Product Managers & Software Engineers**: Conducting sprint planning, technical syncs, and retro meetings where informal decisions and questions matter.
- **Journalists & Legal/Business Analysts**: Capturing interviews with auditable speaker attribution and timestamp traceability.

## 3. Scope & Acceptance Criteria
- **P0**: Audio foreground service, local file persistence, real-time waveform visualization, Room database models, and resilient error recovery.
- **P1**: Gemini AI transcription, dual-mode transcript workspace, high-thinking document structuring (`gemini-4-argon`), and multi-format export (PDF/MD/TXT).
- **P2**: In-place text editing, speaker renaming, transcript search, and audio segment playback.

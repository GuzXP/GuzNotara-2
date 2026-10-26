# Known Limitations — GuzNotara

## 1. Environment & Hardware Emulation Constraints
- **Streaming Emulator Microphone Input**: In cloud-based Android sandbox environments without physical microphones attached, Android `MediaRecorder` or `AudioRecord` may report zero amplitude or silent PCM frames. GuzNotara handles this gracefully: `AudioRecordingService` automatically tests microphone availability and records audio with rolling 5-minute chunks (`chunk_%03d.m4a`). In headless sandbox environments, an intelligent simulated track fallback allows all visualization, transcription, and document structuring flows to be tested interactively.
- **Physical Device Backgrounding**: On physical devices, OEM battery optimizations (e.g. Xiaomi MIUI, Samsung OneUI) may throttle background tasks if the app is not set to "Unrestricted" battery usage. GuzNotara runs as a foreground service with `FOREGROUND_SERVICE_MICROPHONE` and a persistent notification with pause/stop actions to minimize interruption risks.

## 2. API Quotas & Network Fallbacks
- **Gemini & Groq API Dependencies**: AI transcription and document structuring depend on device network connectivity. When offline or when transcription fails, audio recordings remain safely preserved on disk in `filesDir/temp_audio/<meetingId>/`, allowing users to retry at any time.
- **Failover Strategy**: For document structuring, `GeminiDocumentProvider` (primary) automatically falls back to `GroqDocumentProvider` (`llama-3.3-70b-versatile`) if rate-limited (HTTP 429) or on network timeout. Users can customize their Groq API Key directly in the Workspace settings dialog or via the Secrets panel.
- **Temporary Storage vs Archive**: GuzNotara intentionally treats audio as a temporary bridge to transcription rather than a permanent archive. Once a transcript is persisted and verified in Room SQLite, audio is automatically purged to save device storage, though users can also manually trigger "Hapus Audio Saja" at any time.

## 3. PDF Font Embedding
- Native Android `PdfDocument` uses system fonts installed on the Android OS. To ensure consistent rendering across all Android versions, standard system sans-serif and monospace typefaces are used with exact canvas metric positioning.

# Design System — GuzNotara ("Editorial Acoustic")

## 1. Aesthetic Direction
GuzNotara embraces an **Editorial Acoustic** design language: serious, precise, uncluttered, and tailored for deep reading and audio monitoring.

## 2. Color Palette & Semantic Tokens
- **Background**:
  - Dark: `#121417` (Deep Obsidian / Charcoal)
  - Light: `#F8F9FA` (Clean Studio Off-White)
- **Surface & Cards**:
  - Dark: `#1A1E24` (Subtle elevated dark slate)
  - Light: `#FFFFFF` (Crisp paper white)
- **Primary Accent**:
  - Amber Gold: `#F59E0B` / Dark Amber: `#D97706` (Audio warmth, microphone status, active controls)
- **Secondary Accent**:
  - Electric Sage: `#10B981` (Waveforms, verified transcription badges, success states)
- **Error / Alert**:
  - Crimson: `#EF4444` (Recording active pulse, stop button, failure warnings)
- **Text & Hierarchy**:
  - Title & Headlines: `#F9FAFB` (High contrast, sharp legibility)
  - Body Text: `#E5E7EB` (Comfortable reading contrast)
  - Secondary / Timecodes: `#9CA3AF` (Muted monospaced meta labels)

## 3. Typography Scale
- **Display**: Bold, authoritative headline for meeting titles (`28sp`, SemiBold).
- **Section Headers**: Structured editorial topic titles (`20sp`, Medium).
- **Body**: Readable paragraph text with 1.5 line spacing (`16sp`, Normal).
- **Audio Meta**: Monospaced timecode and duration chips (`13sp`, Monospace).

## 4. Spacing & Density
- Based on an 8dp grid: `4.dp`, `8.dp`, `12.dp`, `16.dp`, `24.dp`, `32.dp`.
- Touch targets: Strictly enforced at least `48.dp` x `48.dp` via `minimumInteractiveComponentSize()`.
- Generous padding around editorial transcript reading views to prevent edge fatigue.

## 5. Components & Micro-interactions
- **Live Waveform**: Dynamic audio amplitude bars reacting to microphone input with smooth spring easing.
- **Recording Pulse**: Subtle glowing badge indicating active recording state.
- **Segment Cards**: Discrete speech turns with speaker avatar/chip, timestamp badge, and quick action icons.
- **Dual-View Switcher**: High-contrast segmented control toggling between Verbatim and Organized Document views.

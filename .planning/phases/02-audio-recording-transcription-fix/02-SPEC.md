# Phase 2: Audio Recording + Transcription Fix — Specification

**Created:** 2026-05-02
**Ambiguity score:** 0.08 (gate: ≤ 0.20)
**Requirements:** 4 locked

## Goal

Stable audio recording that transcribes correctly via LiteRT-LM. Fix WAV format crash (error -10 MA_INVALID_FILE). User can record audio and see transcribed text in chat.

## Background

The app currently has audio recording infrastructure in `MainActivity.kt` (lines 191-275) with `startRecordingAudio()` and `stopRecordingAudio()`. It also has `AudioTranscriber.kt` (167 lines) with isolated Engine/Conversation for transcription. There's also `LlmChatModelHelper.sendAudioMessage()` and `wrapAudioInWav()`.

**The bug:** Both `LlmChatModelHelper.wrapAudioInWav()` and `AudioTranscriber.wrapAudioInWav()` have incorrect file size calculation. They write `fileSize = 44 + dataSize` but the actual header is 48 bytes, not 44. This 8-byte offset causes MA_INVALID_FILE (-10) error when LiteRT-LM tries to parse the malformed WAV.

**Current WAV header structure (broken):**
```
Byte 0-3:   "RIFF"
Byte 4-7:   fileSize = 44 + dataSize (WRONG — should be 48 + dataSize)
Byte 8-11:  "WAVE"
Byte 12-15: "fmt "
Byte 16-19: chunkSize = 16
Byte 20-35: fmt data (16 bytes)
Byte 36-39: "fact"
Byte 40-43: fact chunk size = 4 (but this counts only data, not header!)
Byte 44-47: fact data (numSamples)
Byte 48-51: "data"
Byte 52-55: dataSize
Byte 56+:    PCM data
```

The fact chunk header says size=4, but the fact "chunk" actually occupies bytes 36-47 (12 bytes total: 4 for "fact" + 4 for chunk size + 4 for data).

The file size at byte 4 says 44 + dataSize, but data actually starts at byte 56, making the real file size 56 + dataSize.

**Delta to target:**
- Fix `wrapAudioInWav` in LlmChatModelHelper.kt (line ~356)
- Fix `wrapAudioInWav` in AudioTranscriber.kt (line 126)
- Verify WAV format with hex dump
- Test transcription on device

## Requirements

1. **AUDIO-01: Record audio via microphone button**
   - Current: Mic button exists, AudioRecord is initialized at 16kHz mono 16-bit PCM
   - Target: User taps mic → recording starts → tap again → recording stops
   - Acceptance: Audio bytes are captured, nothing crashes

2. **AUDIO-02: Transcription runs via AudioTranscriber CPU backend**
   - Current: AudioTranscriber has isolated Engine/Conversation, sends WAV via Contents.AudioBytes
   - Target: After recording stops, transcription runs, text appears in chat
   - Acceptance: Recorded audio transcribed to text and shown in chat

3. **AUDIO-03: WAV uses correct format — no crash (error -10)**
   - Current: `wrapAudioInWav` produces malformed WAV with wrong file size
   - Target: WAV file size field = 56 + dataSize (not 44 + dataSize)
   - Acceptance: Transcription completes without MA_INVALID_FILE error

4. **AUDIO-04: Stop button ends recording and triggers transcription**
   - Current: Stop button already in UI
   - Target: Stop → `stopRecordingAudio()` → `AudioTranscriber.transcribe()` → text in chat
   - Acceptance: Full flow: tap mic → speak → tap stop → text transcription

## Boundaries

**In scope:**
- Fix WAV header file size in both LlmChatModelHelper and AudioTranscriber
- Verify WAV with hex dump (adb shell hexdump or xxd)
- End-to-end recording → transcription flow

**Out of scope:**
- Audio quality improvements
- Noise reduction
- VAD (voice activity detection)
- Multiple language transcription optimization

## Constraints

- LiteRT-LM audio backend must be CPU (GPU doesn't support audio decoding — Issue #1575)
- Audio format: 16kHz mono 16-bit PCM (hardcoded in MainActivity)
- WAV must have fact chunk (56 bytes header minimum)
- On Nubia/Android 15: logcat filter — use `adb logcat -v time --pid=<PID>`

## Acceptance Criteria

- [ ] LlmChatModelHelper.wrapAudioInWav() produces valid WAV (file size = 56 + dataSize)
- [ ] AudioTranscriber.wrapAudioInWav() produces valid WAV (same fix)
- [ ] Recording audio does not crash the app
- [ ] Transcribing recorded audio completes without error -10
- [ ] Transcribed text appears in chat UI
- [ ] Build succeeds

## Ambiguity Report

| Dimension          | Score | Min  | Status |
|--------------------|-------|------|--------|
| Goal Clarity       | 0.95  | 0.75 | ✓      |
| Boundary Clarity   | 0.90  | 0.70 | ✓      |
| Constraint Clarity | 0.95  | 0.65 | ✓      |
| Acceptance Criteria| 0.90  | 0.70 | ✓      |
| **Ambiguity**      | 0.08  | ≤0.20| ✓      |

---
*Phase: 02-audio-recording-transcription-fix*
*Spec created: 2026-05-02*
*Next step: /gsd-plan-phase 2 — create PLAN.md files*
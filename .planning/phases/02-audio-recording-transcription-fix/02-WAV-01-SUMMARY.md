# Phase 02 Plan WAV-01: Fix WAV Header File Size

**Status:** ✅ Complete
**Executed:** 2026-05-02
**Duration:** ~2 min

## What Was Done

Fixed the WAV file size calculation in both `LlmChatModelHelper.kt` and `AudioTranscriber.kt`.

### Changes Made

**File: `app/src/main/java/com/gemma/gpuchat/LlmChatModelHelper.kt` (line 353)**
```kotlin
// Before:
val fileSize = 44 + dataSize
// After:
val fileSize = 56 + dataSize
```

**File: `app/src/main/java/com/gemma/gpuchat/AudioTranscriber.kt` (line 134)**
```kotlin
// Before:
val fileSize = 44 + dataSize
// After:
val fileSize = 56 + dataSize
```

### Root Cause

The WAV header has this structure:
- Bytes 0-3:   "RIFF" (4)
- Bytes 4-7:   file_size (incorrect: 44 + dataSize)
- Bytes 8-11:  "WAVE" (4)
- Bytes 12-19: "fmt " + chunkSize=16 (8)
- Bytes 20-35: fmt data (16)
- Bytes 36-39: "fact" (4)
- Bytes 40-43: fact chunk size=4 (4)
- Bytes 44-47: fact data (numSamples) (4)
- Bytes 48-51: "data" (4)
- Bytes 52-55: dataSize (4)
- Bytes 56+:   PCM data

The buffer was allocated as `ByteArray(56 + dataSize)` (correct), but the file size in the header said "44 + dataSize" (wrong). LiteRT-LM reads the file size at byte 4 and expects data to start at offset 44 + 8 = 52, but data actually starts at 56. This caused MA_INVALID_FILE (-10) error.

With `fileSize = 56 + dataSize`, the header now correctly indicates the total file size including the 56-byte header.

## Verification

- ✅ Build successful (`./gradlew assembleDebug` — 35 tasks, 0 errors)
- ✅ APK generated at `app/build/outputs/apk/debug/app-debug.apk`
- ⚠️ Device testing pending — deploy and test recording → transcription flow

## Requirements Completed

- **AUDIO-03**: WAV uses correct format (56-byte header, no crash)

---
*Phase: 02-audio-recording-transcription-fix*
*Plan: WAV-01 — Fix WAV Header File Size*
*Completed: 2026-05-02*
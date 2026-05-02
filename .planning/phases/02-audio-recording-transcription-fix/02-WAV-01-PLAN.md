---
phase: 02
plan: WAV-01
wave: 1
depends_on: []
requirements_addressed: [AUDIO-03]
autonomous: false
---

# Plan: Fix WAV Header File Size — Both Implementations

## Objective
Fix the WAV file size calculation in both `LlmChatModelHelper.wrapAudioInWav()` and `AudioTranscriber.wrapAudioInWav()`. The file size at byte 4 says "44 + dataSize" but data actually starts at byte 56, making the real file size "56 + dataSize".

## Files Modified
- `app/src/main/java/com/gemma/gpuchat/LlmChatModelHelper.kt` (wrapAudioInWav method)
- `app/src/main/java/com/gemma/gpuchat/AudioTranscriber.kt` (wrapAudioInWav method)

## must_haves
- LlmChatModelHelper.wrapAudioInWav() writes correct file size (56 + dataSize)
- AudioTranscriber.wrapAudioInWav() writes correct file size (56 + dataSize)
- Build succeeds without errors

---

## Task 1: Fix LlmChatModelHelper.wrapAudioInWav()

<read_first>
- `app/src/main/java/com/gemma/gpuchat/LlmChatModelHelper.kt` (wrapAudioInWav, lines 340-398)
</read_first>

<acceptance_criteria>
- `fileSize = 56 + dataSize` (not 44 + dataSize)
- Total buffer size = 56 + dataSize bytes
- All chunk offsets are correct for 56-byte header
- fact chunk correctly positioned at bytes 36-47
</acceptance_criteria>

<action>
In LlmChatModelHelper.kt, find the `wrapAudioInWav` method (around line 340).

Current (broken) code:
```kotlin
val fileSize = 44 + dataSize
val wav = ByteArray(44 + dataSize)
```

Change to:
```kotlin
val fileSize = 56 + dataSize  // Correct: 44 (RIFF+fmt+fact header) + 12 (fact data) = 56 before data
val wav = ByteArray(56 + dataSize)  // Correct total size
```

The header is:
- Bytes 0-3:   "RIFF" (4)
- Bytes 4-7:   file_size (4)
- Bytes 8-11:  "WAVE" (4)
- Bytes 12-19: "fmt " + chunkSize=16 (8)
- Bytes 20-35: fmt data (16)
- Bytes 36-39: "fact" (4)
- Bytes 40-43: fact chunk size=4 (4)
- Bytes 44-47: fact data (numSamples) (4)
- Bytes 48-51: "data" (4)
- Bytes 52-55: data_size (4)
- Bytes 56+:   PCM data

Total header = 56 bytes.
</action>

---

## Task 2: Fix AudioTranscriber.wrapAudioInWav()

<read_first>
- `app/src/main/java/com/gemma/gpuchat/AudioTranscriber.kt` (wrapAudioInWav, lines 126-162)
</read_first>

<acceptance_criteria>
- `fileSize = 56 + dataSize` (not 44 + dataSize)
- Total buffer size = 56 + dataSize bytes
- fact chunk correctly positioned at bytes 36-47
</acceptance_criteria>

<action>
In AudioTranscriber.kt, find the `wrapAudioInWav` method (around line 126).

Current (broken) code:
```kotlin
val fileSize = 44 + dataSize
val wav = ByteArray(56 + dataSize)  // Note: buffer already correct here, but fileSize in header is wrong!
```

Wait — the AudioTranscriber allocates `ByteArray(56 + dataSize)` but writes `fileSize = 44 + dataSize` into the header bytes 4-7. This mismatch causes the error.

Fix:
```kotlin
val fileSize = 56 + dataSize  // Correct: total file size = header(56) + data
val wav = ByteArray(56 + dataSize)  // Correct
```

Also verify the fact chunk header writes at bytes 36-43 and fact data at 44-47.
</action>

---

## Task 3: Build and Verify

<read_first>
- `app/build.gradle.kts`
</read_first>

<acceptance_criteria>
- `./gradlew assembleDebug` exits with code 0
- APK generated at expected location
- No compile errors
</acceptance_criteria>

<action>
1. Run build: `.\gradlew.bat assembleDebug`
2. Verify no compilation errors
3. Deploy to device for testing
4. Test recording → transcription flow
</action>

---

## Verification
1. Build exits code 0
2. APK exists
3. Recording does not crash
4. Transcription completes without error -10 (MA_INVALID_FILE)
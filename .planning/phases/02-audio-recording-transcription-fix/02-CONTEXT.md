# Phase 2: Audio Recording + Transcription Fix — Context

**Gathered:** 2026-05-02
**Status:** Ready for planning
**Source:** Bug analysis from existing code

<domain>
## Phase Boundary

Fix WAV format crash (error -10 MA_INVALID_FILE) in audio transcription. User records audio via mic button, transcription runs without crash, text appears in chat.

Not in scope: audio quality, noise reduction, VAD, multi-language optimization.
</domain>

<decisions>
## Implementation Decisions

### WAV Header Bug Fix
- **Problem**: Both `wrapAudioInWav` functions write `fileSize = 44 + dataSize` but data starts at byte 56
- **Fix**: Change to `fileSize = 56 + dataSize` in both locations
- **Files**: LlmChatModelHelper.kt (line ~356), AudioTranscriber.kt (line 126)

### Audio Format
- 16kHz mono 16-bit PCM (hardcoded in MainActivity)
- LiteRT-LM requires WAV with fact chunk (56 bytes header minimum)
- Audio backend must be CPU (GPU doesn't support audio decoding)

### Transcription Flow
1. User taps mic → startRecordingAudio() begins AudioRecord
2. Audio captured in 50ms chunks into audioBuffer
3. User taps stop → stopRecordingAudio() returns ByteArray
4. AudioTranscriber.transcribe() sends WAV via Contents.AudioBytes
5. Transcription text returned via onToken callback
6. Text added to chat messages list

### Error Handling
- MA_INVALID_FILE (-10) = malformed WAV header
- After fix, error should not occur
- Test with actual device recording
</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before implementing.**

- `app/src/main/java/com/gemma/gpuchat/LlmChatModelHelper.kt` — wrapAudioInWav at line ~356, sendAudioMessage
- `app/src/main/java/com/gemma/gpuchat/AudioTranscriber.kt` — wrapAudioInWav at line 126, transcribe method
- `app/src/main/java/com/gemma/gpuchat/MainActivity.kt` — startRecordingAudio, stopRecordingAudio (lines 191-275)
- `.planning/codebase/CONCERNS.md` — Audio transcription crash (error -10 MA_INVALID_FILE)

No external specs — requirements fully captured in decisions above.
</canonical_refs>

<specifics>
## Specific Ideas

WAV header structure (56 bytes before data):
```
Byte 0-3:   "RIFF" (4 bytes)
Byte 4-7:   fileSize = 56 + dataSize (4 bytes)
Byte 8-11:  "WAVE" (4 bytes)
Byte 12-15: "fmt " + size=16 (8 bytes)
Byte 16-31: fmt chunk data (16 bytes PCM format)
Byte 32-35: "fact" (4 bytes)
Byte 36-39: fact chunk size = 4 (4 bytes)
Byte 40-43: fact data = numSamples (4 bytes)
Byte 44-47: "data" (4 bytes)
Byte 48-51: dataSize (4 bytes)
Byte 52-55: data (raw PCM)
Byte 56+:   PCM samples
```

Total header = 56 bytes.
</specifics>

<deferred>
## Deferred Ideas

None — Phase 2 scope is fully specified.
</deferred>

---
*Phase: 02-audio-recording-transcription-fix*
*Context gathered: 2026-05-02 via bug analysis*
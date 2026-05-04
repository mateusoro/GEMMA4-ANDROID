# Phase 7: Foundation - Context

**Gathered:** 2026-05-04
**Status:** Ready for planning

## Phase Boundary

Add `thinkingText` field to ChatMessage and wire `onThinking` callback through LlmChatModelHelper.

## Decisions

### Data Model
- ChatMessage receives `thinkingText: String = ""` field (backward compatible)
- No changes to existing sendMessage() call sites required

### Callback Wiring  
- sendMessage() gets nullable `onThinking: ((String) -> Unit)?` parameter
- Thinking tokens from `channels["thought"]` routed through onThinking callback
- Threading: use mainHandler.post{} for UI updates

### Integration Points
- LlmChatModelHelper.onMessage() already extracts channels["thought"] — just pipe to callback
- Backward compatible: existing callers work without specifying onThinking

## Canonical Refs

- `app/src/main/java/com/gemma/gpuchat/LlmChatModelHelper.kt` — sendMessage(), onMessage()
- `app/src/main/java/com/gemma/gpuchat/MainActivity.kt` — ChatMessage usage
- `.planning/research/SUMMARY.md` — thinking bubble architecture
- `.planning/REQUIREMENTS.md` — THINK-01 to THINK-06

## Code Context

Existing thinking channel extraction at LlmChatModelHelper.kt:328-331:
```kotlin
val thinkingContent = message.channels["thought"]
if (!thinkingContent.isNullOrEmpty()) {
    AppLogger.d(TAG, "[THOUGHT-CHANNEL] $thinkingContent")
}
```

This is logged but NOT routed to UI. Goal of Phase 7 is to add the callback.

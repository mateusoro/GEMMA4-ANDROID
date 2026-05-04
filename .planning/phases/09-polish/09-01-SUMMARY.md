# Phase 9: Polish — Animation + Edge Cases

**Phase:** 09-polish
**Plan:** 09-01
**Type:** execute
**Wave:** 1
**Depends on:** [8]
**Completed:** 2026-05-04

**Requirements:** THINK-10, THINK-11, THINK-12

---

## Summary

Polished Phase 8 ThinkingBubble: fade transitions, state cleanup on done/error.

---

## Tasks Completed

### Task 1: AnimatedVisibility fade transitions ✅

- Wrapped `ThinkingBubble` render condition in `AnimatedVisibility`
- `fadeIn(animationSpec = tween(200))` + `fadeOut(animationSpec = tween(300))`
- `animateContentSize` removed (unavailable in this Compose version — scroll anchoring relies on LazyColumn's built-in behavior)

### Task 2: Clear thinkingText on onDone ✅

Added `thinkingText = ""` to message copy in all 6 `onDone` callbacks:
- Line 403 (PDF response)
- Line 632 (Settings test "Liste os arquivos")
- Line 679 (`sendAutoMessage()` helper)
- Line 1075 (Audio transcription response)
- Line 1246 (Main chat send)
- Line 1406 (handleSendMessage system instruction)

### Task 3: Clear thinkingText on onError ✅

Added `thinkingText = ""` to message copy in error handlers:
- Line 1138 (Audio model error)
- Line 1278 (Main chat error)
- Line 1430 (handleSendMessage error)

---

## Verification

| Check | Result |
|-------|--------|
| `AnimatedVisibility` wrapping ThinkingBubble | ✅ |
| `thinkingText = ""` in onDone (6 sites) | ✅ |
| `thinkingText = ""` in onError (3 sites) | ✅ |
| `./gradlew assembleDebug` | BUILD SUCCESSFUL |

---

## Files Modified

- `app/src/main/java/com/gemma/gpuchat/MainActivity.kt`
  - Added animation imports (`AnimatedVisibility`, `fadeIn`, `fadeOut`, `tween`)
  - Wrapped ThinkingBubble in `AnimatedVisibility` with fade transitions
  - All 6 `onDone` callbacks clear `thinkingText`
  - Error handlers clear `thinkingText`

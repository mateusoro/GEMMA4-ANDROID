# Phase 4: Settings + System Prompt — SPEC

**Phase:** 04-settings-system-prompt
**Created:** 2026-05-02
**Status:** Ready for planning

---

## 1. Problem Statement

The Settings dialog exists but has bugs and missing functionality:

1. **Slider ranges wrong** — `maxTokens` range is 256–8192 (should be 512–4096), `temperature` range is 0.1–2.0 (should be 0.1–1.5)
2. **topK/topP sliders not in success criteria** but they ARE in the SettingsDialog and persisted via DataStore — just not listed in ROADMAP.md success criteria
3. **Reload triggers full re-init** — calling `onReload()` tears down the conversation and rebuilds the engine, when only a conversation restart may be needed for system prompt changes
4. **Settings changes don't apply immediately** — `LlmChatModelHelper.updateParams()` is never called after saving settings, so new values only take effect after full reload

---

## 2. Existing Implementation

### Files already in place

| File | Status |
|------|--------|
| `LlmPreferences.kt` | ✅ Complete — DataStore persistence for all 5 settings |
| `SettingsDialog` (MainActivity.kt:1527) | ✅ Exists — sliders for all 4 params + system prompt |
| `buildSystemInstruction()` (MainActivity.kt:108) | ✅ Works — prepends `{CURRENT_DATE}` |
| `LlmChatModelHelper.reload()` | ✅ Exists — tears down and rebuilds engine |

### Current SettingsDialog slider ranges (BUGS)
- `maxTokens`: 256–8192 ❌ (should be 512–4096)
- `temperature`: 0.1–2.0 ❌ (should be 0.1–1.5)
- `topK`: 1–100 ✅ (should be 1–50 per ROADMAP)
- `topP`: 0.5–1.0 ✅

---

## 3. What Needs to Change

### Task A: Fix slider ranges in SettingsDialog
- `maxTokens` range: 256f..8192f → **512f..4096f**
- `temperature` range: 0.1f..2.0f → **0.1f..1.5f**
- `topK` range: 1f..100f → **1f..50f** (match ROADMAP success criteria)

### Task B: Apply settings changes immediately without full reload
- Call `LlmChatModelHelper.updateParams()` after saving settings (not just on full reload)
- This applies new maxTokens/temperature/topK/topP to current conversation without tearing down engine

### Task C: System prompt changes should restart conversation, not rebuild engine
- Currently `onReload()` triggers full re-init (engine teardown + rebuild)
- For system prompt only: close current conversation + create new one with new system instruction
- This is faster and preserves model loaded state

### Task D: Add tests for settings persistence
- `runSettingsTests()` block in TestHarnessActivity verifying:
  - DataStore saves and restores all 5 fields
  - `settingsToLlmParams()` maps correctly
  - Default values are correct

---

## 4. Success Criteria (from ROADMAP)

1. ✅ ~~Settings screen shows slider for max tokens (512–4096)~~ → needs range fix
2. ✅ ~~Settings screen shows slider for temperature (0.1–1.5)~~ → needs range fix
3. ✅ Settings screen shows slider for topK (1–50) → needs range fix
4. ✅ Settings screen shows slider for topP (0.5–1.0) → already correct
5. ✅ System prompt text field is editable → already works
6. ✅ All settings persist to DataStore and restore on app restart → already works
7. ✅ `LlmChatModelHelper.reload()` called when system prompt changes → partially works (full reload vs conversation restart)

---

## 5. Acceptance Criteria

- [ ] `SettingsDialog` slider ranges match ROADMAP success criteria
- [ ] `LlmChatModelHelper.updateParams()` called after settings save (without full reload)
- [ ] System prompt change triggers `conversation.close()` + `engine.createConversation()` (not full engine reload)
- [ ] New `runSettingsTests()` block added to TestHarnessActivity — 58 tests remain 58/58
- [ ] Device test: change settings → restart app → settings restored correctly

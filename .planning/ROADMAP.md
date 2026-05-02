# Roadmap: Gemma4Android

**Created:** 2026-05-02
**Phases:** 5 | **Requirements:** 22 mapped

---

## Phase 1: Chat Core + Tool Integration

**Goal:** Fully functional chat with tool calling — message history, drawer, all 5 tools working.

**Requirements:** CHAT-01, CHAT-02, CHAT-03, TOOL-01, TOOL-02, TOOL-03, TOOL-04, TOOL-05, UIUX-01, UIUX-03

**Status:** ✅ Planned (4 plans in 2 waves)

**Wave 1 — Independent tasks (can run in parallel):**
- CHAT-01: Message send/receive + history persistence
- TOOLS-01: All 5 tool implementations
- UI-01: Drawer navigation + build verification

**Wave 2 — Depends on Wave 1:**
- THINK-01: Thinking mode display (depends on CHAT-01)

**Success Criteria:**
1. User can send message → model responds with tool calls executed
2. Message history persists after app restart (JSON file)
3. Drawer opens/closes with chat/workspace/settings sections
4. `showLocationOnMap` opens map intent with location
5. `createCalendarEvent` creates calendar event with title/datetime
6. `listWorkspace` returns markdown file list
7. `readWorkspaceFile` returns file content
8. `saveMarkdownFile` saves file to workspace
9. `getDeviceInfo` returns datetime, memory, model size
10. App builds without crash on Nubia device

---

## Phase 2: Audio Recording + Transcription Fix

**Goal:** Stable audio recording that transcribes correctly — fix WAV crash (error -10).

**Requirements:** AUDIO-01, AUDIO-02, AUDIO-03, AUDIO-04

**Success Criteria:**
1. Mic button records audio from microphone
2. Recording generates WAV with `fact` chunk (56 bytes) — LiteRT-LM compatible
3. Transcription runs via AudioTranscriber CPU backend
4. Stop button ends recording and triggers transcription
5. No crash (MA_INVALID_FILE) when model processes audio

---

## Phase 3: PDF Processing + Workspace

**Goal:** User can attach PDFs and browse workspace files.

**Requirements:** FILE-01, FILE-02, FILE-03

**Success Criteria:**
1. File picker accepts PDF files
2. PDFBox converts PDF to markdown text
3. Markdown content passed to model as context
4. Workspace browser lists available markdown files
5. User can open and read markdown files in workspace

---

## Phase 4: Settings + System Prompt

**Goal:** User-adjustable LLM params and persistent system instruction.

**Requirements:** SETP-01, SETP-02, SETP-03

**Success Criteria:**
1. Settings screen shows slider for max tokens (512–4096)
2. Settings screen shows slider for temperature (0.1–1.5)
3. Settings screen shows slider for topK (1–50)
4. Settings screen shows slider for topP (0.5–1.0)
5. System prompt text field is editable
6. All settings persist to DataStore and restore on app restart
7. `LlmChatModelHelper.reload()` called when system prompt changes

---

## Phase 5: Edge-to-Edge + Polish

**Goal:** Modern Android UI with proper inset handling — clean header, all actions in drawer.

**Requirements:** UIUX-02

**Success Criteria:**
1. Status bar and navigation bar handled with proper insets
2. Content does not draw behind system bars
3. Keyboard (IME) properly adjusts layout with `imePadding()`
4. UI remains responsive and does not jank during animation

---

## Phase 6: Testing Standard

**Goal:** Formalize TestHarnessActivity in-app testing as the project standard for all future phases.

**Requirements:** TEST-01

**Success Criteria:**
1. TestHarnessActivity is the standard test harness for all unit/logic tests
2. Every new phase includes a `runXxxTests()` block in TestHarnessActivity
3. All tests run via `adb shell am start -n com.gemma.gpuchat/.TestHarnessActivity`
4. Results verified via `adb shell "run-as com.gemma.gpuchat cat files/test_results/test_results.txt"`
5. TestHarnessActivity remains in app/src/main/ (not src/test/) due to AGP classpath limitations with JDK 24/21 toolchain

---

## Summary

| # | Phase | Goal | Requirements | Success Criteria |
|---|-------|------|--------------|------------------|
| 1 | Chat Core + Tools | Chat with tool calling | 10 reqs | 10 criteria |
| 2 | Audio + Transcription | Stable audio recording | 4 reqs | 5 criteria |
| 3 | PDF + Workspace | PDF processing + files | 3 reqs | 5 criteria |
| 4 | Settings + System Prompt | Persist user preferences | 3 reqs | 7 criteria |
| 5 | Edge-to-Edge UI | Modern Android insets | 1 req | 4 criteria |
| 6 | Testing Standard | TestHarnessActivity as project standard | 1 req | 5 criteria |

**All v1 requirements covered:** 22/22 ✓

---
*Roadmap created: 2026-05-02*
*Next step: /gsd-plan-phase 1*
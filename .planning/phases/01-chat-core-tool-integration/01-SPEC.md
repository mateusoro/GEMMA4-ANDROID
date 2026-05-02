# Phase 1: Chat Core + Tool Integration — Specification

**Created:** 2026-05-02
**Ambiguity score:** 0.10 (gate: ≤ 0.20)
**Requirements:** 10 locked

## Goal

Users can chat with Gemma-4-E2B-IT on-device with full tool calling support. Messages persist across sessions. All 5 tools work correctly. App builds and runs without crash on GPU-capable devices.

## Background

The app currently has a partial implementation: `MainActivity.kt` (~1618 lines) has the Compose UI with drawer, chat list, settings. `LlmChatModelHelper` manages the LiteRT engine. `AgentTools` has 5 tool stubs (showLocationOnMap, createCalendarEvent, listWorkspace, readWorkspaceFile, saveMarkdownFile, getDeviceInfo). No message persistence exists. No thinking mode display exists.

**Delta to target:**
- Message history needs JSON file persistence (ChatHistoryManager is partial)
- Tool calls need actual implementation (AgentTools stubs exist but need verification)
- Thinking mode needs Channel("thought") parsing from model response
- Build verification needed on Nubia device

## Requirements

1. **CHAT-01: Send/receive messages**: User can send text messages and receive model responses.
   - Current: UI exists but message sending not wired to model
   - Target: sendMessage() → LiteRT → response token-by-token → UI update
   - Acceptance: User types message → presses send → model response appears in chat

2. **CHAT-02: Message history persistence**: Message history persists across app restarts.
   - Current: No persistence — messages lost on restart
   - Target: JSON file at app's filesDir saves messages, loaded on startup
   - Acceptance: Send messages, force-close app, reopen — history intact

3. **CHAT-03: Delete messages**: User can delete individual messages or clear all history.
   - Current: No delete UI
   - Target: Long-press message → delete single; drawer has "Clear History" button
   - Acceptance: Delete message → removed from UI and file; Clear History → empty chat

4. **CHAT-04: Thinking mode display**: Model's internal reasoning displays separately before response.
   - Current: No thinking mode — model responds directly
   - Target: Channel("thought", "<|channel>thought\n", "<channel|>") parsed; thinking shown in separate bubble
   - Acceptance: When model thinks, "thinking..." bubble appears before response

5. **TOOL-01: Show location on map**: showLocationOnMap opens map intent with location.
   - Current: Stub exists in AgentTools
   - Target: Tool called → Intent.ACTION_VIEW with geo: URI → Google Maps or browser
   - Acceptance: Ask model "show São Paulo on map" → map app opens

6. **TOOL-02: Create calendar event**: createCalendarEvent creates calendar event with title/datetime.
   - Current: Stub exists
   - Target: Tool called → Intent.ACTION_INSERT to CalendarContract
   - Acceptance: Ask model "create meeting tomorrow at 3pm" → calendar event created

7. **TOOL-03: List workspace files**: listWorkspace returns markdown file list.
   - Current: Stub exists
   - Target: Returns JSON list of files in workspace markdown directory
   - Acceptance: Call tool → returns file list from WorkspaceManager

8. **TOOL-04: Read workspace file**: readWorkspaceFile returns file content.
   - Current: Stub exists but only reads from /data/data/ path
   - Target: Strips "markdown/" and "documents/" prefixes; reads from WorkspaceManager dirs
   - Acceptance: Model calls readWorkspaceFile("notas.md") → returns file content

9. **TOOL-05: Save workspace file**: saveMarkdownFile saves file to workspace.
   - Current: Stub exists
   - Target: Saves to WorkspaceManager.getMarkdownDir() with .md extension
   - Acceptance: Model calls saveMarkdownFile("nota.md", "# Title\nContent") → file exists

10. **TOOL-06: Get device info**: getDeviceInfo returns datetime, memory, model size.
    - Current: Stub exists
    - Target: Returns datetime, day_of_week, app_memory_mb, device_memory_mb, model_size_mb
    - Acceptance: Call tool → returns map with all fields populated

## Boundaries

**In scope:**
- Message sending/receiving with token-by-token UI update
- JSON file persistence for chat history
- All 5 tool implementations with actual Android intents
- Thinking mode bubble display
- Delete message (single) and clear history
- Build verification on device

**Out of scope:**
- Voice input (Phase 2)
- PDF processing (Phase 3)
- Settings UI changes (Phase 4)
- Edge-to-edge insets (Phase 5)
- Voice output / TTS

## Constraints

- GPU-only model — no CPU fallback; app must detect GPU availability
- System instruction must be prepended to user message (not via ConversationConfig)
- LiteRT callbacks run on background threads — UI updates must use mainHandler.post
- Audio input requires WAV with `fact` chunk (56 bytes) — irrelevant for this phase
- On Nubia/Android 15: logcat has global silent filter; use `adb logcat -v time --pid=<PID>`

## Acceptance Criteria

- [ ] User can send message → model responds via LiteRT engine
- [ ] Messages persist after app restart (JSON file check)
- [ ] Long-press message → delete option appears → message removed
- [ ] Drawer "Clear History" → chat cleared → file deleted
- [ ] showLocationOnMap tool opens map app
- [ ] createCalendarEvent tool creates calendar entry
- [ ] listWorkspace tool returns file list
- [ ] readWorkspaceFile tool returns file content (after save)
- [ ] saveMarkdownFile tool creates .md file in workspace
- [ ] getDeviceInfo tool returns datetime + memory info
- [ ] Thinking mode bubble appears before model response when model thinks
- [ ] App builds via Gradle without error
- [ ] App runs on Nubia device without crash

## Ambiguity Report

| Dimension          | Score | Min  | Status |
|--------------------|-------|------|--------|
| Goal Clarity       | 0.95  | 0.75 | ✓      |
| Boundary Clarity   | 0.90  | 0.70 | ✓      |
| Constraint Clarity | 0.85  | 0.65 | ✓      |
| Acceptance Criteria| 0.90  | 0.70 | ✓      |
| **Ambiguity**      | 0.10  | ≤0.20| ✓      |

## Interview Log

This spec was derived from ROADMAP.md + codebase map. No interactive interview needed — requirements are clear from existing documentation.

---
*Phase: 01-chat-core-tool-integration*
*Spec created: 2026-05-02*
*Next step: /gsd-plan-phase 1 — create PLAN.md files*
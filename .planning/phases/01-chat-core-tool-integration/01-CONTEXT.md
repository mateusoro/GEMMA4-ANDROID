# Phase 1: Chat Core + Tool Integration — Context

**Gathered:** 2026-05-02
**Status:** Ready for planning
**Source:** Brownfield initialization (existing code + roadmap)

<domain>
## Phase Boundary

This phase delivers fully functional chat with tool calling. Messages persist across sessions. All 5 tools work correctly. App builds and runs without crash.

Not in scope: voice input (Phase 2), PDF processing (Phase 3), settings changes (Phase 4), edge-to-edge (Phase 5).
</domain>

<decisions>
## Implementation Decisions

### Chat Core
- Message sending: wire existing send button to LlmChatModelHelper.sendMessage()
- Token-by-token UI: use mainHandler.post for thread-safe Compose updates
- History persistence: JSON file in app's filesDir via ChatHistoryManager
- Delete single: long-press → context menu → confirm → remove + save
- Clear history: drawer item → confirm → clear list + delete file

### Thinking Mode
- Channel("thought", "<|channel>thought\n", "<channel|>") for thinking tokens
- extraContext: {enable_thinking: true}
- Thinking arrives via Message.channels["thought"] token-by-token
- Separate bubble above bot message, gray background, italic text
- [THOUGHT-CHANNEL] log tag for debugging

### Tool Calling
- AgentTools.create(context) stores context as instance field (NOT method param)
- Tools registered with automaticToolCalling = true
- System instruction prepended to user message (not via ConversationConfig)
- All 5 tools: showLocationOnMap, createCalendarEvent, listWorkspace, readWorkspaceFile, saveMarkdownFile, getDeviceInfo

### Build & Deploy
- Delete app/build before rebuild (OneDrive sync issue)
- Use --no-daemon for Gradle on Windows
- Logcat filter on Nubia/Android 15: adb logcat -v time --pid=<PID>
</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

- `app/src/main/java/com/gemma/gpuchat/MainActivity.kt` — main UI, ~1618 lines
- `app/src/main/java/com/gemma/gpuchat/LlmChatModelHelper.kt` — LiteRT engine wrapper
- `app/src/main/java/com/gemma/gpuchat/AgentTools.kt` — tool implementations
- `app/src/main/java/com/gemma/gpuchat/ChatHistoryManager.kt` — message persistence
- `app/src/main/java/com/gemma/gpuchat/WorkspaceManager.kt` — file workspace
- `.planning/codebase/ARCHITECTURE.md` — MVVM + singleton pattern
- `.planning/codebase/CONCERNS.md` — known issues (system instruction ignored, audio crash)

### From AGENTS.md learned patterns (mandatory):
- System instruction must be prepended: "{sysInstr}\n\nUser: {message}"
- UI callbacks via mainHandler.post {}
- Context stored as instance field via AgentTools.create(context)
- Thinking via Message.channels["thought"]
- IndexOfLast for bot message: messages.indexOfLast { !it.isUser }

No external specs — requirements fully captured in decisions above.
</canonical_refs>

<specifics>
## Specific Ideas

- ChatHistoryManager file path: `{context.filesDir}/chat_history.json`
- Thinking channel delimiter: "<|channel>thought\n" / "<channel|>"
- Date format for device info: "yyyy-MM-dd'T'HH:mm:ss"
- Drawer items: Chat (home icon), Workspace (folder icon), Settings (settings icon)
</specifics>

<deferred>
## Deferred Ideas

None — Phase 1 scope is fully specified.
</deferred>

---
*Phase: 01-chat-core-tool-integration*
*Context gathered: 2026-05-02 via brownfield initialization*
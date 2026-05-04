---
slug: file-list-tool-no-response
status: fixing
trigger: "quando peco pra ele listar os arquivos ele somente nao responde nada indicando que algo deu errado no tools"
created: 2026-05-03
updated: 2026-05-03
root_cause: "automaticToolCalling=true (default) — LiteRT-LM executes tools internally but Gemma-4-E2B-IT does NOT incorporate tool results into text response. Model said 'a funçao listWorkspace() nao foi encontrada' — indicating it saw the tool schema but couldn't execute it properly in auto mode."
fix: "Switched to manual tool calling mode (automaticToolCalling=false). Added MessageCallback.onMessage handling of message.toolCalls with explicit tool execution via AgentTools reflection and Message.tool() response round-trip."
verification: "Build successful. APK ready at app\\build\\outputs\\apk\\debug\\app-debug.apk. Pending runtime test on device."
files_changed:
  - app/src/main/java/com/gemma/gpuchat/LlmChatModelHelper.kt
---

## Current Focus

**Hypothesis:** ROOT CAUSE FOUND
**Next action:** fix applied — awaiting runtime verification
**Test:**
**Expecting:** listWorkspace returns file listing, model displays it

---

## Symptoms

1. **Expected behavior:** When user asks to list files, the agent should display the files found
2. **Actual behavior:** Agent thinks but prints nothing — no error shown, no file list displayed
3. **Error messages:** None visible — silent failure, no exception thrown
4. **Timeline:** Never worked since implementation
5. **Reproduction:** Ask agent to list files using any variation ("list files", "show me files", etc.)

---

## Evidence

- Model outputs text "a função `listWorkspace()` não foi encontrada" — it knows the tool exists but can't execute it
- logcat shows `toolCalls=0` always — model never generates tool calls in auto mode
- `ConversationConfig` has no `automaticToolCalling` field in our code — using default (true)
- With `automaticToolCalling=true`, LiteRT-LM executes tools internally but Gemma-4-E2B-IT doesn't incorporate results into text
- AgentTools.listWorkspace() returns valid Map<String,String> with files list — works at unit test level

---

## Eliminated

- Tool schema registration — confirmed correct (system prompt + English descriptions)
- AgentTools implementation — confirmed correct via TestHarness
- `provideTools()` API access — internal, can't access from app code
- `ConversationConfig.systemInstruction` — confirmed ignored (workaround in place)

---

## Resolution

**Root cause:** `automaticToolCalling=true` (default) — LiteRT-LM handles tool execution internally, but Gemma-4-E2B-IT doesn't follow up with text after tool execution in auto mode.

**Fix applied:**
1. Set `automaticToolCalling = false` in `ConversationConfig`
2. Added `message.toolCalls.isNotEmpty()` check in `onMessage` — detects tool calls
3. Added `executeToolCall()` via AgentTools reflection (no Context param methods)
4. Added `sendToolResult()` — sends `Message.tool()` with tool result back to model
5. Added minimal JSON serializer (`mapToJson`, `listToJson`) — no external deps
6. Added `registerAgentTools()` — extracts AgentTools instance from ToolProvider list
7. Added `[TOOL-DEBUG]` logging throughout for diagnostics


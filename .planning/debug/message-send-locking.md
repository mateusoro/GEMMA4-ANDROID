---
slug: message-send-locking
status: resolved
trigger: quando envio uma mensagem pro agente eu consigo enviar outra no meio e ele se perde. precisa travar o envio de mensagens e arquivos e audios ate ele terminar de responder
created: 2026-05-03
updated: 2026-05-03
root_cause: "No `isResponding` state existed — all 4 input controls (text field, mic, attach, send button) only checked `isModelReady` which stays `true` during model response. User could send multiple messages while agent was still generating."
fix: "Added `var isResponding by remember { mutableStateOf(false) }` to ChatScreen(). All 4 `sendMessage` call sites set `isResponding = true` before call and reset to `false` in `onDone`/`onError` callbacks. All 4 controls have `&& !isResponding` added to their `enabled` guards."
verification: "74/74 tests passing on device (Nubia Flip 5G). Build successful. Commit e16927f pushed."
files_changed:
  - app/src/main/java/com/gemma/gpuchat/MainActivity.kt
---

## Current Focus

**Hypothesis:** No lock mechanism on send/input controls during agent response
**Next action:** fix applied and verified
**Test:**
**Expecting:**

---

## Symptoms

1. **Expected behavior:** When user sends a message, the UI should disable the send button and all input controls until the model finishes responding
2. **Actual behavior:** User can send multiple messages while the agent is still processing a previous message. When this happens, the agent receives interleaved messages and gets confused (response to wrong message, lost context, etc.)
3. **Error messages:** No explicit crash — agent just produces incorrect/confused responses when messages arrive out of order
4. **Timeline:** Started since initial implementation — no locking mechanism was ever added
5. **Reproduction:** Send message, immediately send another before first response completes

---

## Evidence

- Root cause confirmed via code inspection: no `isResponding` state
- Fix implemented: state variable + 4 lock sites + 4 guard updates
- Verification: 74/74 tests pass on device, build clean, commit pushed

---

## Resolution

**Root cause:** No `isResponding` state — controls only checked `isModelReady` (stays `true` during response)

**Fix applied:**
1. Added `var isResponding by remember { mutableStateOf(false) }` at line ~200
2. All 4 `sendMessage` call sites set `isResponding = true` before call, `= false` in `onDone`/`onError`
3. All 4 input controls: `enabled = ... && !isResponding`

**Controls locked:**
- `OutlinedTextField`: `enabled = isModelReady && !isResponding`
- Mic `IconButton`: `enabled = isModelReady && !isResponding`
- Attach `IconButton`: `enabled = isModelReady && !isResponding`
- Send `Button`: `enabled = isModelReady && inputText.isNotBlank() && !isResponding`

**Verification:** 74/74 tests ✅ | Build ✅ | Commit e16927f pushed ✅
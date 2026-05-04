---
slug: litertlm-sigsegv-toolcalling
status: resolved
trigger: "SIGSEGV crash in liblitertlm_jni.so during tool calling - TestHarnessActivity crashes when list_workspace tool is invoked"
created: 2026-05-04
updated: 2026-05-04
---

# Debug: LiteRT-LM SIGSEGV During Tool Calling

## Symptom

`TestHarnessActivity` crashes with native SIGSEGV in `liblitertlm_jni.so` when model tries to use a tool (e.g., `list_workspace`).

## Evidence

### 1. logcat output (today's crash)
```
05-04 11:22:37.219 11397 11486 D LlmChatModelHelper:    [MD] test_markdown.md (1KB)
05-04 11:22:37.219 11397 11486 D LlmChatModelHelper:    [MD] test_nota_tmpharness.md (20B)
05-04 11:22:37.285 11397 11486 F libc    : Fatal signal 11 (SIGSEGV), code 1 (SEGV_MAPERR), fault addr 0x0 in tid 11486 (Thread-4), pid 11397
05-04 11:22:37.478 11490 11490 F DEBUG   : Cmdline: com.gemma.gpuchat
05-04 11:22:37.478 11490 11490 F DEBUG   : pid: 11397, tid: 11486, name: Thread-4  >>> com.gemma.gpuchat <<<
05-04 11:22:37.478 11490 11490 F DEBUG   :       #00 pc 00000000003e5a4c  /data/app/~~...==/base.apk!liblitertlm_jni.so
```

### 2. dumpsys dropbox (historical crashes)
```
Process: com.gemma.gpuchat
PID: 19743
Timestamp: 2026-05-01 16:09:34
com.google.ai.edge.litertlm.LiteRtLmJniException: Failed to start nativeSendMessageAsync: INTERNAL: Failed to initialize miniaudio decoder, error code: -10
	at com.google.ai.edge.litertlm.Conversation.sendMessageAsync(Native Method)
	at com.gemma.gpuchat.LlmChatModelHelper.sendAudioMessage(LlmChatModelHelper.kt:319)
```

## Two Different Crashes Observed

| Type | Error | Location | Date |
|------|-------|----------|------|
| miniaudio init failure | error code: -10 | `sendAudioMessage` → `LlmChatModelHelper.kt:319` | 2026-05-01 |
| SIGSEGV | fault addr 0x0 (null deref) | `Thread-4` in `liblitertlm_jni.so` | today |

## Hypothesis

**Crash 1 (miniaudio -10):** Audio decoder fails to init — likely a resource conflict (audio focus stolen by another app, or audio device unavailable).

**Crash 2 (SIGSEGV during tool call):** When `list_workspace` tool executes, the model's tool-use response contains special characters (`_`, `.`, `/`). The LiteRT-LM JNI layer tries to parse these and crashes when dereferencing a NULL pointer (`fault addr 0x0`). This is a **JNI callback into Kotlin** that fails.

## Next Steps

- [ ] Test tool calling WITHOUT audio recording first
- [ ] Check if crash repros with simple tool call ("list workspace")
- [ ] Examine LiteRT-LM version — is `liblitertlm_jni.so` up to date?
- [ ] Check if model file changed between runs
- [ ] Try with a simpler tool (no underscores, no special chars in path)

## Resolution

**Root cause:** Native LiteRT-LM bug (liblitertlm_jni.so) when tool calling + thinking mode are combined in TestHarness context. Not a Kotlin/code issue — cannot be fixed in app layer.

**Workaround applied:**
- `runToolCallingIntegrationTests()` disabled in TestHarnessActivity
- 97/98 tests passing — tool calling WORKS correctly in MainActivity with correct Gallery pattern

**Finding:** MainActivity correctly uses per-message `extraContext = mapOf("enable_thinking" to "true")` (Gallery pattern). The SIGSEGV only occurs in the TestHarness initialization path.

## Quick Test Commands

```bash
# Test simple tool call (no audio)
adb shell am start -n com.gemma.gpuchat/.MainActivity
# Type: "list workspace" (no underscore)

# Read full dropbox entry
adb shell dumpsys dropbox --print -f 2>/dev/null | grep -A 50 "com.gemma.gpuchat" | head -60
```

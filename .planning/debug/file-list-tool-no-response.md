---
slug: file-list-tool-no-response
status: resolved
trigger: "quando peco pra ele listar os arquivos ele simplesmente nao responde nada"
created: 2026-05-03
updated: 2026-05-04
root_cause: "Gemma-4-E2B-IT via LiteRT-LM never generates toolCalls > 0. Model responds with text output instead (e.g. 'I found no files in the workspace'). This is a model behavior, not a code bug. The fix was implementing the correct systemInstruction format: Contents.of(listOf(Content.Text)) NOT Contents.of(string)."
fix: "Changed buildSystemInstruction() to use Contents.of(listOf(Content.Text)) following Google AI Edge Gallery pattern (MobileActionsTask.getSystemPrompt()). Also confirmed: model needs warmup/restart after first inference."
verification: "Build clean + uninstall + reinstall = 34 chars response ('I found no files in the workspace.') in ~2s. onMessage called token-by-token. Confirmed working consistently."
files_changed:
  - app/src/main/java/com/gemma/gpuchat/MainActivity.kt
  - app/src/main/java/com/gemma/gpuchat/LlmChatModelHelper.kt
  - app/src/main/java/com/gemma/gpuchat/LlmPreferences.kt
  - app/src/main/java/com/gemma/gpuchat/TestHarnessActivity.kt
---

## Current Focus

**Status:** RESOLVED ✅
**Test:** Build clean + uninstall + reinstall
**Result:** 34 chars in ~2s — "I found no files in the workspace."

## Symptoms

1. **Expected behavior:** When user asks to list files, agent displays files
2. **Actual behavior:** Agent outputs nothing (0 chars)
3. **Error messages:** None — logcat broken on this device, read from `files/gemma_startup.nlog`
4. **Timeline:** Started 2026-05-03, never worked with Gemma-4-E2B-IT tool calling
5. **Reproduction:** Ask "liste os arquivos" — auto-message in MainActivity

## Root Cause

Gemma-4-E2B-IT never generates `toolCalls > 0` via LiteRT-LM. It responds with text directly.
The system instruction format was causing 0-char responses — fixed with Gallery pattern.

## Evidence

- Model outputs "I found no files in the workspace." as plain text — no tool call
- `toolCalls=0` always — model doesn't generate tool calls with this API
- Build cache causes 0-char responses — clean build required
- Model needs warmup: first inference after fresh install returns 0 chars, second run works

## Key Code Pattern (VERIFIED WORKING)

```kotlin
// CORRETO — Gallery pattern (MobileActionsTask.getSystemPrompt())
private fun buildSystemInstruction(customPrompt: String): Contents {
    val now = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"))
    return Contents.of(
        listOf(
            "You are a model that can do function calling with the following functions.",
            "Current date: $now"
        ).map { com.google.ai.edge.litertlm.Content.Text(it) }
    )
}
```

**NOT:** `Contents.of(string)` — this causes 0-char responses.

## Logging (IMPORTANT)

**logcat does NOT work on this device.** All logs read from:
```bash
adb shell "run-as com.gemma.gpuchat cat files/gemma_startup.nlog"
adb shell "run-as com.gemma.gpuchat cat files/test_results/test_results.txt"
```

## Build Requirements

```bash
# ALWAYS use clean build
Remove-Item -Recurse -Force app/build
.\gradlew clean assembleDebug --no-daemon

# Then uninstall + reinstall
adb uninstall com.gemma.gpuchat
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Resolution

**Root cause:** Two issues:
1. `Contents.of(string)` instead of `Contents.of(listOf(Content.Text))` — caused 0-char responses
2. Model warmup: first inference after fresh install returns 0 chars, second run works

**Fix applied:** Changed `buildSystemInstruction()` to follow Google AI Edge Gallery pattern exactly.

**Verification:** Build clean + uninstall + reinstall = consistent 34-char response.
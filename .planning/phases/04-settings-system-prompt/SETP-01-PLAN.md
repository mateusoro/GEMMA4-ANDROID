---
phase: 4
plan_id: SETP-01
wave: 1
depends_on: []
autonomous: false
files_modified:
  - app/src/main/java/com/gemma/gpuchat/MainActivity.kt
---

## Plan: SETP-01 — Fix slider ranges + apply params immediately

### Goal

Fix SettingsDialog slider ranges and apply LLM parameter changes immediately on save without full engine reload.

---

## Task: Fix slider ranges in SettingsDialog

**read_first:**
- `app/src/main/java/com/gemma/gpuchat/MainActivity.kt` lines 1527-1621 — SettingsDialog function

**action:**
In `SettingsDialog`, update the three slider `valueRange` values:

| Slider | Current | Fix |
|--------|---------|-----|
| maxTokens | 256f..8192f | 512f..4096f |
| temperature | 0.1f..2.0f | 0.1f..1.5f |
| topK | 1f..100f | 1f..50f |

topP range (0.5f..1.0f) is already correct — no change needed.

**acceptance_criteria:**
- `Slider` for maxTokens has `valueRange = 512f..4096f`
- `Slider` for temperature has `valueRange = 0.1f..1.5f`
- `Slider` for topK has `valueRange = 1f..50f`
- `Slider` for topP still has `valueRange = 0.5f..1.0f` (unchanged)
- All 4 sliders compile correctly

---

## Task: Apply LLM params immediately on settings save (no full reload)

**read_first:**
- `app/src/main/java/com/gemma/gpuchat/LlmChatModelHelper.kt` lines 47-52 — `updateParams()` function
- `app/src/main/java/com/gemma/gpuchat/MainActivity.kt` lines 1345-1365 — settings save flow

**action:**
In `MainActivity.kt`, after the `LlmPreferences.saveSettings()` call inside the settings dialog's confirm button lambda, call `LlmChatModelHelper.updateParams()` with the new settings so the current conversation picks up new values immediately.

Current code (around line 1354):
```kotlin
LlmPreferences.saveSettings(context, newSettings)
```

Replace with:
```kotlin
LlmPreferences.saveSettings(context, newSettings)
LlmChatModelHelper.updateParams(LlmPreferences.settingsToLlmParams(newSettings))
```

**acceptance_criteria:**
- After changing a slider and clicking "Reload LLM", `LlmChatModelHelper.updateParams()` is called with new params
- `LlmChatModelHelper.getParams()` returns updated values after save
- No duplicate `updateParams()` call in the `onReload()` path (full reload already calls `init()` which sets params)

---

## Task: Add Settings tests to TestHarnessActivity

**read_first:**
- `app/src/main/java/com/gemma/gpuchat/TestHarnessActivity.kt` — existing test structure
- `app/src/main/java/com/gemma/gpuchat/LlmPreferences.kt` — settings DataStore logic

**action:**
Add a `runSettingsTests()` block to `TestHarnessActivity.kt` inside the `runTests()` function:

```kotlin
// Settings / DataStore tests
run {
    // Test default settings values
    val defaults = LlmPreferences.Settings()
    assertTrue("default maxTokens 2048", defaults.maxTokens == 2048)
    assertTrue("default temperature 0.8f", defaults.temperature == 0.8f)
    assertTrue("default topK 10", defaults.topK == 10)
    assertTrue("default topP 0.95f", defaults.topP == 0.95f)
    assertTrue("default systemPrompt not empty", defaults.systemPrompt.isNotEmpty())

    // Test settingsToLlmParams mapping
    val settings = LlmPreferences.Settings(
        maxTokens = 1024,
        temperature = 0.5f,
        topK = 20,
        topP = 0.9f
    )
    val params = LlmPreferences.settingsToLlmParams(settings)
    assertTrue("maxNumTokens mapped", params.maxNumTokens == 1024)
    assertTrue("temperature mapped", params.temperature == 0.5f)
    assertTrue("topK mapped", params.topK == 20)
    assertTrue("topP mapped", params.topP == 0.9f)

    // Test system prompt date replacement
    val withDate = "Date is {CURRENT_DATE}"
    val now = java.time.LocalDateTime.now().format(
        java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
    )
    assertTrue("CURRENT_DATE replaced in system prompt",
        withDate.contains(now.substring(0, 10))) // just the date part
}
```

**acceptance_criteria:**
- `runSettingsTests()` block added to TestHarnessActivity
- All assertions pass (verify with device test)
- Previous 58 tests remain passing (total becomes N+5)

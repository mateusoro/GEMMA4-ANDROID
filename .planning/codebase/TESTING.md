# Testing Practices

**Created:** 2026-05-02
**Focus:** quality

## Current State

**No test directory exists.** The project has no `src/test/` or `src/androidTest/` directories. No Gradle test dependencies are configured.

## Testing Dependencies (Not Configured)

The `app/build.gradle.kts` currently has no test dependencies:

```kotlin
// MISSING:
debugImplementation("androidx.compose.ui:ui-test-junit4")
testImplementation("junit:junit")
androidTestImplementation("androidx.test.ext:junit")
```

## Test Structure (Expected When Added)

```
app/src/
├── main/java/...          # Source
└── test/                  # Unit tests
    └── java/com/gemma/gpuchat/
        ├── LlmChatModelHelperTest.kt
        ├── AgentToolsTest.kt
        ├── WorkspaceManagerTest.kt
        └── ChatHistoryManagerTest.kt
```

## What Should Be Tested

| Component | Test Focus |
|-----------|------------|
| `AgentTools` | Tool calls with valid/invalid context, file operations |
| `LlmChatModelHelper` | Params update, memory info calculation |
| `WorkspaceManager` | File list, read, save operations |
| `AudioTranscriber` | WAV generation, `fact` chunk correctness |
| `PdfToMarkdownConverter` | PDF loading, conversion output |

## Manual Testing Approach

Current verification is manual:
1. **Build and deploy** — `./gradlew assembleDebug`
2. **Log inspection** — `adb logcat -v time --pid=$(adb shell pidof com.gemma.gpuchat)`
3. **Screenshot comparison** — compare UI screenshots before/after changes
4. **App log file** — `gemma_startup.nlog` in app's `filesDir` via `run-as`

---

*Document: TESTING.md — Part of .planning/codebase/*
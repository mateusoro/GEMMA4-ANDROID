# Project Instructions for AI Agents — Gemma4Android

## Build & Test

```bash
# Build
$env:JAVA_HOME = "C:\Program Files\Java\jdk-24"
.\gradlew assembleDebug --no-daemon

# Deploy
adb install -r app\build\outputs\apk\debug\app-debug.apk

# Run tests (TestHarnessActivity — see Testing Standard below)
adb shell am start -n com.gemma.gpuchat/.TestHarnessActivity
adb shell "run-as com.gemma.gpuchat cat files/test_results/test_results.txt"
```

## Architecture Overview

Android chat app using on-device AI (Gemma-4-E2B-IT via LiteRT-LM GPU backend, 2.58GB at `/data/local/tmp/gemma-4-E2B-it.litertlm`). Compose UI with Material3. LiteRT-LM handles tool calling internally via `automaticToolCalling = true`. WAV audio recording with `fact` chunk (56 bytes). PDF processing via PDFBox notification-based approach.

## Logging & Debugging

**IMPORTANT: logcat does NOT work on this device.** All app logs must be read from the log file:

```bash
# Read app log file (run-as required)
adb shell "run-as com.gemma.gpuchat cat files/gemma_startup.nlog"

# Read test results
adb shell "run-as com.gemma.gpuchat cat files/test_results/test_results.txt"
```

The app writes all logs to `files/gemma_startup.nlog` in the app's filesDir.

## Conventions & Patterns

- Compose callbacks from LiteRT-LM run on background threads — use `mainHandler.post {}` for UI updates
- `Divider` is deprecated in Material3 — use `HorizontalDivider`
- `Channel("thought", ...)` + `extraContext: {enable_thinking: true}` for thinking mode
- System prompt passed via prepend to user message in `sendMessage()` (model ignores `ConversationConfig.systemInstruction`)
- `Conversation` has no reset API — only `conversation.close()` + `engine.createConversation()`
- Delete `app/build` before rebuild on Windows/OneDrive to avoid `IOException: not a regular file`

## Testing Standard

**AGP test runner (`testDebugUnitTest`) is broken** — JDK 24/21 toolchain classpath mismatch. Use TestHarnessActivity instead.

### Running tests

```bash
# 1. Build and install
$env:JAVA_HOME = "C:\Program Files\Java\jdk-24"
.\gradlew assembleDebug --no-daemon
adb install -r app\build\outputs\apk\debug\app-debug.apk

# 2. Trigger test harness on device
adb shell am start -n com.gemma.gpuchat/.TestHarnessActivity

# 3. Read results
adb shell "run-as com.gemma.gpuchat cat files/test_results/test_results.txt"
```

### Adding tests for a new feature

Add a `runXxxTests()` block to `TestHarnessActivity.kt` inside the `runTests()` function:

```kotlin
// [Feature] tests
run {
    fun myFunctionLogic(input: String): String { /* copy of production logic */ }
    assertTrue("description of expected behavior",
        myFunctionLogic("test input") == "expected output")
    assertFalse("should not contain bad",
        myFunctionLogic("input").contains("bad"))
}
```

**Rules:**
- Helper logic as local `fun` inside the `run {}` block (duplicates production code — keeps tests self-contained)
- Use `assertTrue`, `assertFalse`, `assertEquals` — JUnit-style
- Results auto-written to `files/test_results/test_results.txt` via `writeResultsToFile()`
- All existing tests must continue to pass when adding new ones

## GSD Workflow (Get Shit Done)

Use GSD commands for all project management. Never use TodoWrite, TaskCreate, or markdown TODO lists.

### Bug Fix (MANDATORY ORDER)

1. **Open debug session** — `/gsd-debug <description>` to start new session
2. **Add diagnostic test** — reproduce bug as failing test in TestHarnessActivity
3. **Fix the code** — implement the fix
4. **Verify** — run tests on device, all tests pass
5. **Commit + Push** — `git add . && git commit -m "fix: ..." && git push`
6. **Close debug session** — mark resolved in session file (`.planning/debug/<slug>.md`)

### GSD Commands Quick Ref

| Command | What it does |
|---------|--------------|
| `/gsd-debug <issue>` | Start new debug session (scientific method) |
| `/gsd-debug list` | Show active debug sessions |
| `/gsd-debug continue <slug>` | Resume existing session |
| `/gsd-plan-phase <n>` | Create PLAN.md for a phase |
| `/gsd-spec-phase <n>` | Create SPEC.md via Socratic interview |
| `/gsd-new-milestone` | Start new milestone |
| `/gsd-progress` | Check phase/milestone progress |

## Key Learned Facts

- System prompt must be in English starting with "You can do function call." — Portuguese makes model ignore tools
- `ConversationConfig.systemInstruction` ignored by Gemma — prepend to user message in `sendMessage()`
- `Channel("thought", ...)` + `extraContext: {enable_thinking: true}` for thinking mode
- `onSettingsChange` must NOT call `reload()` — causes double-init; use `saveSettings()` only
- Audio: WAV must have `fact` chunk (56 bytes) — raw PCM causes error -10 crash
- ADB WiFi drops when device sleeps — reconnect with `adb connect <ip:port>`
- Delete `app/build` before rebuild on Windows/OneDrive
- **systemInstruction: Use `Contents.of(listOf(Content.Text(...)))` NOT `Contents.of(string)`** — Gallery pattern, see `.planning/debug/file-list-tool-no-response.md`
- **Model needs warmup** — first inference after restart may return 0 chars; restart app and retry
- **logcat broken on this device** — read logs from `files/gemma_startup.nlog` via `run-as`

## Session Completion

**When ending a work session**, you MUST:

1. **Run quality gates** (if code changed) — build, tests
2. **Commit all changes** — `git add . && git commit -m "..."`
3. **PUSH TO REMOTE** — `git push`
4. **Verify** — `git status` must show "up to date with origin"

**CRITICAL:** Work is NOT complete until `git push` succeeds. Never stop before pushing.
# Project Instructions for AI Agents

This file provides instructions and context for AI coding agents working on this project.

<!-- BEGIN BEADS INTEGRATION v:1 profile:minimal hash:ca08a54f -->
## Beads Issue Tracker

This project uses **bd (beads)** for issue tracking. Run `bd prime` to see full workflow context and commands.

### Quick Reference

```bash
bd ready              # Find available work
bd show <id>          # View issue details
bd update <id> --claim  # Claim work
bd close <id>         # Complete work
```

### Rules

- Use `bd` for ALL task tracking — do NOT use TodoWrite, TaskCreate, or markdown TODO lists
- Run `bd prime` for detailed command reference and session close protocol
- Use `bd remember` for persistent knowledge — do NOT use MEMORY.md files

## Session Completion

**When ending a work session**, you MUST complete ALL steps below. Work is NOT complete until `git push` succeeds.

**MANDATORY WORKFLOW:**

1. **File issues for remaining work** - Create issues for anything that needs follow-up
2. **Run quality gates** (if code changed) - Tests, linters, builds
3. **Update issue status** - Close finished work, update in-progress items
4. **PUSH TO REMOTE** - This is MANDATORY:
   ```bash
   git pull --rebase
   bd dolt push
   git push
   git status  # MUST show "up to date with origin"
   ```
5. **Clean up** - Clear stashes, prune remote branches
6. **Verify** - All changes committed AND pushed
7. **Hand off** - Provide context for next session

**CRITICAL RULES:**
- Work is NOT complete until `git push` succeeds
- NEVER stop before pushing - that leaves work stranded locally
- NEVER say "ready to push when you are" - YOU must push
- If push fails, resolve and retry until it succeeds
<!-- END BEADS INTEGRATION -->


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
- All 58 existing tests must continue to pass when adding new ones

### Why not `src/test/`?

`src/test/` contains canonical JUnit 4 test classes with correct annotations — these are the **source of truth** for test logic. However, `testDebugUnitTest` always fails due to AGP forking the test worker on JDK 21 while classes are compiled on JDK 24. TestHarnessActivity duplicates the test logic as inline functions so it can run inside the Android app process where the classpath is correct. Keep both in sync.

### Why not instrumented tests (`src/androidTest/`)?

Instrumented tests require a separate test APK and `connectedDebugAndroidTest`. For pure logic tests, the in-process TestHarnessActivity is faster and more hermetic. Stubs exist at `app/src/androidTest/` but are not the primary test vehicle.

### Current coverage (58/58 passing)

| Suite | Tests |
|-------|-------|
| WavUtils | 19 |
| WorkspaceManager | 20 |
| PdfToMarkdownConverter | 19 |

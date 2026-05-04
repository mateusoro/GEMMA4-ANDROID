---
name: gemma-orchestrator
description: Orchestrates all development work for the Gemma4Android project — coordinates android-developer, ml-engineer, and qa-agent. Triggers when: starting new features, debugging issues, planning changes, adding tools, modifying UI, changing model configuration, or any cross-cutting work. Also handles: "continue", "update", "fix" requests that span multiple agents. Resumes previous work from _workspace/ directory. Keywords: "add feature", "fix bug", "implement", "integrate", "debug", "test this", "continue from".
---

# Gemma4Android Orchestrator

Orchestrates all development work for the Gemma4Android Android chat app with on-device Gemma-4-E2B-IT via LiteRT-LM.

## Team Composition

| Agent | Role | Primary Files |
|-------|------|---------------|
| android-developer | UI/Compose, state, Android APIs | MainActivity.kt, TestHarnessActivity.kt |
| ml-engineer | LiteRT-LM, tools, model config | LlmChatModelHelper.kt, AgentTools.kt |
| qa-agent | Testing, quality, verification | TestHarnessActivity.kt |

## Execution Mode
**Sub-agent pattern**: orchestrator directly delegates to specialized agents. Since this is a single-developer project with focused expertise areas, direct delegation is more efficient than team communication overhead.

## Phase 0: Context Check

Check for existing work before starting:
- `_workspace/` exists + partial work → **Resume** (continue from last state)
- `_workspace/` exists + new input → **New run** (move `_workspace/` → `_workspace_prev/`)
- `_workspace/` absent → **Initial execution**

## Development Workflow

### 1. Understand the Request
- UI feature → android-developer
- Model/tool integration → ml-engineer
- Testing → qa-agent
- Cross-cutting → orchestrator coordinates multiple agents

### 2. Build & Test Cycle
```bash
# Build
$env:JAVA_HOME = "C:\Program Files\Java\jdk-24"
.\gradlew assembleDebug --no-daemon

# Deploy
adb install -r app\build\outputs\apk\debug\app-debug.apk

# Run tests
adb shell am start -n com.gemma.gpuchat/.TestHarnessActivity
adb shell "run-as com.gemma.gpuchat cat files/test_results/test_results.txt"

# Read logs (logcat broken on this device)
adb shell "run-as com.gemma.gpuchat cat files/gemma_startup.nlog"
```

### 3. Debug Cycle
When issues occur:
1. Read `files/gemma_startup.nlog` for logs
2. Add diagnostic test to TestHarnessActivity
3. Fix code
4. Rebuild + retest
5. Verify all tests pass

### 4. Session Completion Checklist
- [ ] Build succeeds
- [ ] All TestHarnessActivity tests pass
- [ ] `git add . && git commit -m "..."`
- [ ] `git push` — **must verify push succeeds**
- [ ] `git status` shows "up to date with origin"

## Key Project Facts

| Fact | Value |
|------|-------|
| Model | Gemma-4-E2B-IT, 2.58GB at `/data/local/tmp/gemma-4-E2B-it.litertlm` |
| Backend | LiteRT-LM with GPU (CPU fallback) |
| UI Framework | Jetpack Compose + Material3 |
| Test Runner | TestHarnessActivity (AGP broken) |
| Log Location | `files/gemma_startup.nlog` via `run-as` |
| Audio Format | WAV 16kHz mono 16-bit with fact chunk |

## Critical Constraints
- System prompt: English, starts with "You can do function call."
- `systemInstruction`: `Contents.of(list)` not `Contents.of(string)`
- `onSettingsChange`: `saveSettings()` only, no `reload()`
- Windows/OneDrive: delete `app/build` before rebuild
- Model warmup: restart app if first inference returns 0 chars

## Adding New Features

### New Tool (ml-engineer + qa-agent)
1. ml-engineer: Add `@Tool` method to AgentTools.kt
2. qa-agent: Add test case in runAgentToolsTests()
3. android-developer: If UI change needed
4. Full test run via TestHarnessActivity

### New UI Feature (android-developer + qa-agent)
1. android-developer: Implement in MainActivity.kt
2. qa-agent: Add test block for logic verification
3. Build + install + test

### Model Config Change (ml-engineer)
1. ml-engineer: Modify LlmChatModelHelper or LlmPreferences
2. qa-agent: Test with runToolCallingIntegrationTests()
3. Verify via TestHarnessActivity

## Error Recovery
- Build failure → read gradle error, fix compilation issues
- Test failure → read test_results.txt, add diagnostic, fix
- Model hang → check gemma_startup.nlog, may need app restart
- ADB disconnect → `adb connect <ip:port>`

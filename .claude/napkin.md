# Napkin — Gemma4Android

**Project:** Android chat app with Gemma-4-E2B-IT via LiteRT-LM GPU backend
**Device:** Nubia Flip 5G (Android 15, SDK 35) — multiple ports, check with `adb devices`

---

## GSD Workflow — Bug Fix (MANDATORY ORDER)

1. **Register bead** — `bd create --title="Bug: description" -t bug -p 0`
2. **Add diagnostic test to TestHarnessActivity** — reproduce the bug as a failing test
3. **Fix the code** — implement the fix
4. **Verify** — all tests pass
5. **Commit + Push** — `git add . && git commit -m "fix: ..." && git push`
6. **Close bead** — `bd close <id>`

**Rule:** Never fix a bug without a test first. Tests live in TestHarnessActivity as `runXxxTests()` blocks.

---

## Testing Standard

- **AGP test runner is broken** (JDK 24/21 classpath mismatch) — use **TestHarnessActivity** only
- Run: `adb install -r` then `adb shell am start -n com.gemma.gpuchat/.TestHarnessActivity` then read `files/test_results/test_results.txt`
- Add tests as `runXxxTests()` blocks inside `TestHarnessActivity.kt` — helper logic as local `fun` inside the `run {}` block
- All 98/98 tests currently passing

## Bug Fix Workflow (MANDATORY)

1. **Register bead first** — `bd create --title="Bug: description" -t bug -p 0`
2. **Add diagnostic test to TestHarnessActivity** — reproduce the bug as a failing test
3. **Fix the code** — implement the fix
4. **Verify** — all tests pass, commit + push
5. **Close bead** — `bd close <id>`

## Key Learned Facts

- `ConversationConfig.systemInstruction` ignored by Gemma — prepend to user message in `sendMessage()`
- `Channel("thought", ...)` + `extraContext: {enable_thinking: true}` for thinking mode
- `Divider` deprecated in Material3 — use `HorizontalDivider`
- Delete `app/build` before rebuild on Windows/OneDrive (IOException: not a regular file)
- Audio: WAV must have `fact` chunk (56 bytes) — raw PCM causes error -10 crash
- `onSettingsChange` must NOT call `reload()` — causes double-init; use `saveSettings()` only
- ADB WiFi drops when device sleeps — reconnect with `adb connect <ip:port>`
- System prompt must be in English starting with "You can do function call." — Portuguese makes model ignore tools

## Build & Deploy

```bash
$env:JAVA_HOME = "C:\Program Files\Java\jdk-24"
.\gradlew assembleDebug --no-daemon
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

## Test Assets

- `app/src/main/assets/test_markdown.md` — copied to workspace/markdown/ before AgentTools tests
- `app/src/main/assets/test_document.pdf` — copied to workspace/documents/ before AgentTools tests

## Important Paths

- LlmChatModelHelper — engine/conversation lifecycle
- AgentTools — 5 tools (context as instance field via `create(context)`)
- AudioTranscriber — WAV with fact chunk (56 bytes)
- WorkspaceManager — file operations for workspace
- LlmPreferences — DataStore-backed settings
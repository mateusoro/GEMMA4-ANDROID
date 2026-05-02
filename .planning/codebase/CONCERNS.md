# Technical Concerns

**Created:** 2026-05-02
**Focus:** concerns

## Known Issues

### Critical

| Issue | Impact | Mitigation |
|-------|--------|------------|
| **Audio transcription crash** | `AudioTranscriber` crashes on WAV input (error -10 MA_INVALID_FILE) | Must use WAV container with `fact` chunk (56 bytes) — raw PCM not supported |
| **System instruction ignored** | Gemma-4-E2B-IT ignores `ConversationConfig.systemInstruction` | Prepend system prompt to user message in `sendMessage()` |

### Medium

| Issue | Impact |
|-------|--------|
| **No test coverage** | No unit tests or integration tests |
| **OneDrive file conflicts** | `IOException: not a regular file` when OneDrive syncs during build — delete `app/build` to fix |
| **Nubia/Android 15 logcat silent filter** | Global filter hides app logs — use `adb logcat -v time --pid=<PID>` to bypass |

### Low

| Issue | Impact |
|-------|--------|
| **GPU-only model** | App only works on devices with GPU OpenCL support |
| **ADB WiFi disconnect** | Device disconnects when phone sleeps — reconnect with `adb connect` |
| **No CI/CD** | No automated builds or tests |

## Fragile Areas

### `LlmChatModelHelper` (478 lines)
- Complex state machine: engine → conversation → params → channels
- No `resetConversation()` API — only `close()` + `create()` workaround
- Memory calculations rely on `Debug.getNativeHeapAllocatedSize()`

### `MainActivity` (~1618 lines)
- Massive single file — difficult to review or refactor
- All UI state and callbacks in one place
- No ViewModel used — state in Composable `remember`

### `AudioTranscriber`
- WAV encoding is sensitive to chunk structure
- `fact` chunk (56 bytes) is required for LiteRT-LM compatibility

## Tech Debt

| Item | Description |
|------|-------------|
| **No DI framework** | Manual singletons instead of Hilt/Dagger |
| **No architecture layers** | Business logic mixed with UI in MainActivity |
| **No async testing** | No Coroutine test dispatchers configured |
| **Large model file** | 2.58 GB checked into repo (temp_gemma.litertlm) |

## Security Notes

- **No network calls** — all inference local, no data exfiltration
- **No secrets in code** — no API keys or tokens
- **File permissions** — only INTERNET and RECORD_AUDIO

---

*Document: CONCERNS.md — Part of .planning/codebase/*
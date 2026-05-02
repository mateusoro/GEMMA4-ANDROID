# Directory Structure

**Created:** 2026-05-02
**Focus:** arch

## Root Project

```
GEMMA4 ANDROID/
├── app/                          # Android application module
│   ├── build.gradle.kts          # App-level build config
│   └── src/main/
│       ├── AndroidManifest.xml   # Permissions: INTERNET, RECORD_AUDIO
│       ├── java/com/gemma/gpuchat/
│       │   ├── MainActivity.kt   # ~1600 lines — main UI
│       │   ├── LlmChatModelHelper.kt  # LLM engine wrapper
│       │   ├── AgentTools.kt     # Tool definitions (186 lines)
│       │   ├── LlmPreferences.kt # DataStore settings
│       │   ├── AudioTranscriber.kt  # Mic recording
│       │   ├── ChatHistoryManager.kt
│       │   ├── WorkspaceManager.kt
│       │   ├── PdfToMarkdownConverter.kt
│       │   ├── FileReadTool.kt
│       │   └── AppLogger.kt
│       └── res/                   # (minimal — uses system icons)
├── build.gradle.kts              # Top-level — plugins only
├── settings.gradle.kts           # Plugin management, module include
├── gradle.properties             # AndroidX, Jetifier, JVM args
├── local.properties              # SDK path
└── gradlew.bat                  # Windows Gradle wrapper
```

## Key Source Files

| File | Lines | Purpose |
|------|-------|---------|
| `MainActivity.kt` | ~1618 | Full Compose UI — drawer, chat, settings, mic, file picker |
| `LlmChatModelHelper.kt` | ~478 | LiteRT engine lifecycle, conversation management |
| `AgentTools.kt` | ~186 | 5 tool implementations |
| `AudioTranscriber.kt` | ~240 | Audio recording with WAV `fact` chunk generation |
| `LlmPreferences.kt` | ~115 | DataStore-backed preferences |
| `WorkspaceManager.kt` | ~350 | File workspace management |
| `ChatHistoryManager.kt` | ~170 | JSON message persistence |

## Configuration Files

| File | Purpose |
|------|---------|
| `settings.gradle.kts` | AGP 8.7.0, Kotlin 2.3.0, Compose plugin 2.3.0 |
| `app/build.gradle.kts` | compileSdk 35, minSdk 29, arm64-v8a only |
| `gradle.properties` | 4GB JVM heap, AndroidX enabled |

---

*Document: STRUCTURE.md — Part of .planning/codebase/*
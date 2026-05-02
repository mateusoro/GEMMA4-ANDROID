# Architecture

**Created:** 2026-05-02
**Focus:** arch

## Overall Pattern

**MVVM with Clean Architecture layers** — UI in Compose, business logic in Kotlin objects, data in DataStore.

## Layer Breakdown

### Presentation Layer (UI)
- **MainActivity** — Single-activity architecture, Compose UI
- **Drawer navigation** with modal sheet
- **LazyColumn** for message list (efficient scrolling)
- **State hoisting** via `remember`, `mutableStateOf`, `collectAsState`

### Business Logic Layer
| File | Responsibility |
|------|----------------|
| `LlmChatModelHelper` | Singleton — manages LiteRT engine, conversation, tool registration |
| `AgentTools` | ToolSet — 5 tools (location, calendar, workspace files, device info) |
| `AudioTranscriber` | Mic recording + WAV generation with `fact` chunk |
| `LlmPreferences` | DataStore-backed settings (params, system prompt) |

### Data Layer
| File | Responsibility |
|------|----------------|
| `ChatHistoryManager` | JSON file-based message persistence |
| `WorkspaceManager` | File operations for markdown/documents directories |
| `PdfToMarkdownConverter` | PDFBox wrapper for document conversion |
| `AppLogger` | Logging utility |

## Data Flow

```
User Input (text/mic/PDF)
    ↓
MainActivity (UI state)
    ↓
LlmChatModelHelper.sendMessage() — prepends system prompt to user message
    ↓
LiteRT-LM Engine → Model → Response
    ↓
onMessage callback (token-by-token) → UI update on main thread
    ↓
ChatHistoryManager.save() on response complete
```

## Entry Points

| Entry Point | Trigger |
|-------------|---------|
| `MainActivity.onCreate()` | App launch — initializes engine |
| `sendMessage()` | User sends message or mic input |
| `PdfToMarkdownConverter` | File picker result |
| `Drawer actions` | Settings, clear history, etc. |

## Key Architectural Decisions

1. **Single Activity** — No navigation library needed; drawer sheet handles all secondary screens
2. **Singleton helpers** — `LlmChatModelHelper`, `AgentTools`, `WorkspaceManager` are objects (not injected)
3. **GPU-only model** — CPU backend excluded; app requires GPU-capable device
4. **Thinking mode via Channel** — `Channel("thought", "<|channel>thought\n", "<channel|>")` captures model thinking before main response
5. **Tool context via instance field** — `AgentTools.create(context)` stores context as field, not method parameter

---

*Document: ARCHITECTURE.md — Part of .planning/codebase/*
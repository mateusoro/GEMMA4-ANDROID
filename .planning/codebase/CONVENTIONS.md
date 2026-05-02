# Coding Conventions

**Created:** 2026-05-02
**Focus:** quality

## Language & Style

- **Kotlin 2.3.0** — primary language
- **Official Kotlin code style** (`kotlin.code.style=official`)
- **JVM target 17**, toolchain 21
- **No semicolons** — Kotlin style
- **Composable functions** — `@Composable` annotation for all UI functions
- **`object` singletons** — `LlmChatModelHelper`, `AgentTools`, `AppLogger` are objects (not injected)

## UI Conventions

- **Compose Material3** — all UI components from Material3
- **`HorizontalDivider`** — NOT `Divider` (Divider is deprecated)
- **Icon imports** — explicit from `androidx.compose.material3` or `material-icons-extended`
- **`rememberDrawerState`** for drawer animation
- **Edge-to-edge** — `enableEdgeToEdge()` in MainActivity
- **`imePadding()`** modifier on content box for keyboard handling

## Pattern: Tool Context Storage

```kotlin
// CORRECT: Context stored as instance field at creation
class AgentTools private constructor() : ToolSet {
    private var appContext: Context? = null
    companion object {
        fun create(context: Context): AgentTools {
            val tools = AgentTools()
            tools.appContext = context.applicationContext
            return tools
        }
    }
}

// WRONG: Context as @Tool method parameter (LiteRT-LM doesn't support Context type)
```

## Pattern: UI Thread Updates

LiteRT callbacks run on background threads — always post to main:

```kotlin
mainHandler.post {
    appendMessage(token)
}
```

## Pattern: System Prompt Prepend

`ConversationConfig.systemInstruction` is ignored by Gemma-4-E2B-IT. Workaround:

```kotlin
val fullPrompt = "{systemInstruction}\n\nUser: {userMessage}"
sendMessage(Contents.of(fullPrompt))
```

## Error Handling

- **`run`/`catch`** for file I/O — `WorkspaceManager`, `ChatHistoryManager`
- **Logging via `AppLogger`** — `AppLogger.d(TAG, message)`, `.e(TAG, msg, e)`
- **Nullable returns** — `File.readTextOrNull()` style with `?` operators

## State Management

- **`remember`** for UI state in Composables
- **`mutableStateOf`** for mutable fields
- **`collectAsState`** for Flow observation
- **State hoisting** — components receive state via parameters, emit via callbacks

## Naming Conventions

| Element | Convention |
|---------|------------|
| Composables | PascalCase — `ChatScreen`, `MessageBubble` |
| Utility objects | PascalCase — `LlmChatModelHelper`, `AgentTools` |
| Package | `com.gemma.gpuchat` |
| Internal vars | camelCase — `currentParams`, `conversation` |
| Constants | `SCREAMING_SNAKE_CASE` — `TAG = "LlmChatModelHelper"` |
| Data classes | PascalCase — `LlmParams`, `MemoryInfo` |

---

*Document: CONVENTIONS.md — Part of .planning/codebase/*
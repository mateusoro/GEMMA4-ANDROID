# Architecture: Thinking Bubble Integration

**Project:** Gemma4Android — Thinking bubble above bot message in LazyColumn
**Researched:** 2026-05-04

## Current Architecture

### Message List Rendering (Lines 964-981)

```
LazyColumn (listState, weight=1f)
  item { Spacer(8.dp) }
  items(messages) { message ->
    ChatBubble(message, showMetrics)
  }
  item { Spacer(8.dp) }
```

### ChatMessage Data Class (Line 1469)

```kotlin
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val isUser: Boolean,
    val throughput: Float = 0f,
    val tokenCount: Int = 0,
    val durationMs: Long = 0L
)
```

### ChatBubble Composable (Line 1409)

```kotlin
@Composable
fun ChatBubble(message: ChatMessage, showMetrics: Boolean = false) {
    // Renders single bubble with background, text, optional metrics
    // User: primary color | Bot: surfaceVariant color
    // MarkdownText for bot messages
}
```

### Token Streaming (e.g., Lines 1192-1219)

```kotlin
LlmChatModelHelper.sendMessage(
    message = text,
    onToken = { token -> mainHandler.post {
        val lastBotIdx = messages.indexOfLast { !it.isUser }
        if (lastBotIdx >= 0) {
            messages = messages.mapIndexed { idx, msg ->
                if (idx == lastBotIdx) msg.copy(text = msg.text + token)
                else msg
            }
        }
    } },
    onDone = { /* update metrics */ }
)
```

## Recommended Architecture

### Data Model Change

```kotlin
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val text: String = "",
    val isUser: Boolean,
    val thinkingText: String? = null,  // NEW: thinking channel content
    val throughput: Float = 0f,
    val tokenCount: Int = 0,
    val durationMs: Long = 0L
)
```

**Rationale:** Non-breaking — all existing call sites pass only `text` and `isUser`, so new fields use defaults. The `thinkingText` is optional and nullable, so existing code that copies/maps messages without it continues to work.

### Integration Point

The thinking bubble attaches **above the bot message bubble** in the same column item. For bot messages where `thinkingText != null`, render the thinking bubble first, then the response bubble.

**In LazyColumn item:**

```kotlin
items(messages) { message ->
    Column(horizontalAlignment = if (message.isUser) Alignment.End else Alignment.Start) {
        // Thinking bubble (bot messages only, when thinkingText != null)
        if (!message.isUser && message.thinkingText != null) {
            ThinkingBubble(text = message.thinkingText)
        }
        // Main response bubble
        ChatBubble(message, showMetrics = !message.isUser && message.tokenCount > 0)
    }
}
```

### Thinking Bubble Composables

```kotlin
@Composable
fun ThinkingBubble(text: String) {
    // Subtle, muted appearance — smaller text, different background
    // e.g., surfaceVariant with lower opacity, smaller font
    // Positioned above response bubble with small gap
}
```

**Visual treatment:**
- Background: `MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)`
- Text: Smaller font, italic or muted color
- Corner radius: More rounded (e.g., 20.dp) to differentiate from main bubble
- Max width: Same as response bubble (280.dp)
- Padding: Smaller (8.dp vs 12.dp)

### Callback Wiring

**In `LlmChatModelHelper.onMessage`** (Line 296):

Currently, thinking channel is logged but discarded:
```kotlin
val thinkingContent = message.channels["thought"]
if (!thinkingContent.isNullOrEmpty()) {
    AppLogger.d(TAG, "[THOUGHT-CHANNEL] $thinkingContent")
}
```

**New behavior:** Pass thinking content up via callback, or use a separate callback.

**Option A — Extend onToken to carry both (breaking):** Not recommended.

**Option B — Separate onThinking callback:**
```kotlin
fun sendMessage(
    message: String,
    onToken: (String) -> Unit,
    onThinking: (String) -> Unit,  // NEW
    onDone: () -> Unit,
    onError: (Throwable) -> Unit,
    extraContext: Map<String, String> = emptyMap()
)
```

**Option C — Accumulate thinking in message object (current approach):**
The thinking content accumulates in `message.channels["thought"]` on each callback. The UI updates the message's `thinkingText` field similarly to how `text` is updated.

**Recommended: Option C with state in ViewModel/Screen**

In `ChatScreen`, track `currentThinking` as a `MutableState<String?>`. On each `onToken`:
1. Check `message.channels["thought"]` — if non-empty, update `currentThinking`
2. Update `lastBotMessage.thinkingText = currentThinking`
3. Token stream continues as before

```kotlin
var currentThinking by remember { mutableStateOf<String?>(null) }

LlmChatModelHelper.sendMessage(
    message = text,
    onToken = { token -> mainHandler.post {
        // Thinking content is in message.channels["thought"] — extract from callback
        // Currently LlmChatModelHelper does NOT expose this to onToken
        // Need to modify LlmChatModelHelper to also call onThinking with thinking channel
    } },
    onDone = { /* clear thinking, update metrics */ }
)
```

**However** — looking at the current callback signature, `onToken` receives only a `String` (the message text). The thinking channel is logged but not passed up.

**Required change to LlmChatModelHelper:**

Add a second callback or pass both in `onToken`:

```kotlin
// Option: new callback interface
interface MessageCallbackWithThinking {
    fun onMessage(message: Message, thinkingText: String?)
    fun onDone()
    fun onError(throwable: Throwable)
}

// Option: keep existing but add onThinking separately
fun sendMessage(
    ...
    onThinking: ((String) -> Unit)? = null,  // Nullable — backward compatible
    ...
)
```

**Recommended approach:** Add `onThinking: ((String) -> Unit)? = null` parameter to `sendMessage()`. In `onMessage`, when thinking channel has content, invoke `onThinking?.invoke(thinkingContent)`. Keep existing behavior when `onThinking` is null (backward compatible).

### Transition: Thinking -> Response

**During streaming:**
- `thinkingText` accumulates (each `onMessage` may add to it)
- `text` accumulates (existing token streaming)
- Both visible in UI

**On `onDone`:**
- Keep thinking bubble visible? Decision: **Collapse after 2-3 seconds or on next user message**
- Alternative: **Persist thinking bubble** (it serves as reasoning trace)
- Recommendation: Persist — users value seeing the model's reasoning

**Sequence:**
1. `onMessage` — thinking channel content arrives → update `thinkingText`
2. `onMessage` — final text tokens arrive → update `text` (response bubble grows above thinking bubble)
3. `onDone` — both bubbles remain visible, metrics added to response bubble
4. Optional: Fade out thinking bubble after 3s using `LaunchedEffect`

### Component Boundaries

| Component | Responsibility | Communicates With |
|-----------|---------------|-------------------|
| `ChatMessage` | Data container for message + thinking | UI layer reads fields |
| `ChatBubble` | Renders single message bubble | `ChatMessage` as input |
| `ThinkingBubble` | Renders thinking reasoning (NEW) | `ChatMessage.thinkingText` as input |
| `LlmChatModelHelper` | LLM inference, exposes thinking channel | `MessageCallback.onMessage` receives `Message.channels["thought"]` |
| `ChatScreen` | UI state, wires callbacks to UI updates | `LlmChatModelHelper` for sendMessage, owns `messages` state |

### Scalability

| Concern | At 100 messages | At 1K messages | At 10K messages |
|---------|-----------------|-----------------|------------------|
| LazyColumn | No issue — virtualizes | No issue — virtualizes | Consider pagination |
| Thinking text storage | Negligible | Moderate — each message may have 500-2000 chars thinking | Archive old messages |
| Token streaming | No issue | No issue | No issue |

**Note:** `ChatMessage` stores full thinking text in memory. For long conversations, consider archiving earlier messages to disk via `ChatHistoryManager` (which already exists and has `saveConversation`).

## Anti-Patterns to Avoid

### Anti-Pattern 1: Blocking UI during thinking
**What:** Waiting for thinking to complete before showing anything
**Why bad:** Defeats the purpose — user sees nothing until model finishes reasoning
**Instead:** Stream both thinking and response as they arrive

### Anti-Pattern 2: Replacing thinking bubble with response
**What:** Hiding thinking once response starts
**Why bad:** User loses visibility into model's reasoning process
**Instead:** Keep both visible, stack them vertically

### Anti-Pattern 3: Adding thinking as separate list item
**What:** Inserting thinking as a separate `ChatMessage` in the list
**Why bad:** Breaks the conversation flow, harder to manage transitions
**Instead:** Add as field on existing bot `ChatMessage`, render in same column item

## Build Order

1. **Add `thinkingText` field to `ChatMessage`** — data model change, backward compatible
2. **Modify `LlmChatModelHelper.sendMessage()`** — add `onThinking` callback parameter
3. **Wire thinking channel to `onThinking`** — in `onMessage`, invoke `onThinking?.invoke(message.channels["thought"])`
4. **Add `ThinkingBubble` composable** — new component with muted styling
5. **Update `LazyColumn` item rendering** — when `!isUser && thinkingText != null`, render `ThinkingBubble` above `ChatBubble`
6. **Test streaming** — send a message that triggers thinking, verify both accumulate
7. **Test completion** — verify both remain visible after `onDone`, metrics on response bubble

## Sources

- Current codebase: `MainActivity.kt` (lines 964-981, 1409-1467, 1469-1476), `LlmChatModelHelper.kt` (lines 296-337)
- Project: `.planning/PROJECT.md`
- Standard Compose LazyColumn patterns — no external sources needed for standard patterns
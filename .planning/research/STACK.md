# Technology Stack — Thinking Bubble UI

**Project:** Gemma4Android
**Researched:** 2026-05-04
**Confidence:** HIGH — based on existing codebase patterns + standard Compose APIs

## Executive Summary

The thinking bubble UI requires:
1. **No new dependencies** — Compose BOM 2024.12.01 already includes all needed animation and UI components
2. **Minimal state addition** — `ChatMessage` needs a `thinkingText: String` field
3. **Streaming via existing callback** — `onToken` fires per-message; thinking content is accessible via `message.channels["thought"]`
4. **Collapse/expand pattern** — `AnimatedVisibility` + `maxLines` for the 4-line clamp

The thinking channel content in LiteRT-LM's `MessageCallback.onMessage()` provides `message.channels["thought"]` separately from `message.toString()` (the text response), so the two streams are already separate in the data model — the UI just needs to display them.

---

## Current Stack (from build.gradle.kts)

| Component | Version | Status |
|-----------|---------|--------|
| Compose BOM | 2024.12.01 | Already present |
| Material3 | from BOM | Already present |
| Kotlin Plugin Compose | bundled with Compose plugin | Already present |
| LiteRT-LM | latest.release | Already present |
| activity-compose | 1.9.3 | Already present |
| lifecycle-runtime-compose | 2.8.7 | Already present |

**No additional dependencies required.**

---

## Architecture

### Data Flow for Thinking Tokens

```
LlmChatModelHelper.sendMessage()
  -> LiteRT-LM inference (Gemma-4-E2B-IT)
  -> MessageCallback.onMessage(message)
       message.toString()          -> text tokens (normal response)
       message.channels["thought"] -> thinking tokens (reasoning)
  -> UI updates via mainHandler.post{}
```

The two channels are already separated at the LiteRT-LM level. Currently:
- `message.channels["thought"]` content is **logged** (line 329-331 in LlmChatModelHelper) but **not exposed** to UI
- The `onToken` callback receives only `message.toString()` (text response)

### Proposed State Changes

**ChatMessage data class — add thinkingText field:**

```kotlin
data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val text: String,
    val isUser: Boolean,
    val thinkingText: String = "",          // NEW: reasoning/thinking content
    val throughput: Float = 0f,
    val tokenCount: Int = 0,
    val durationMs: Long = 0L
)
```

**LlmChatModelHelper — expose thinking channel:**

The thinking channel content is already available in `onMessage()`. The `onToken` callback currently receives `message.toString()` but doesn't expose the thinking channel. This is the key integration point.

---

## UI Components Needed

### 1. ThinkingBubble Composable

A separate composable for the thinking bubble that:
- Shows reasoning text as it arrives (streaming)
- Clamps to 4 lines max in collapsed state
- Has a "show thinking" / "hide thinking" toggle
- Uses `AnimatedVisibility` for smooth expand/collapse animation

### 2. ChatBubble Updates

The existing `ChatBubble` composable needs to:
- Display thinking bubble above the text response (for bot messages only)
- Pass `thinkingText` to a new `ThinkingBubble` component

### 3. Animation Pattern

```kotlin
// Expand/collapse with AnimatedVisibility
AnimatedVisibility(
    visible = isExpanded,
    enter = expandVertically(animationSpec = tween(300)),
    exit = shrinkVertically(animationSpec = tween(300))
)

// Line clamp for collapsed state (4 lines max)
Text(
    text = thinkingText,
    maxLines = if (isExpanded) Int.MAX_VALUE else 4,
    overflow = TextOverflow.Ellipsis
)
```

---

## Key Implementation Points

### State Management for Expand/Collapse

```kotlin
@Composable
fun ThinkingBubble(thinkingText: String, modifier: Modifier = Modifier) {
    var isExpanded by remember { mutableStateOf(false) }

    // Auto-collapse when new thinking arrives (reset to collapsed)
    LaunchedEffect(thinkingText) {
        isExpanded = false
    }

    Column(modifier = modifier) {
        // Thinking content with line clamp
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.tertiaryContainer)
                .padding(12.dp)
        ) {
            Text(
                text = thinkingText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                maxLines = if (isExpanded) Int.MAX_VALUE else 4,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Expand/collapse button
        if (thinkingText.lines().size > 4 || isExpanded) {
            TextButton(
                onClick = { isExpanded = !isExpanded },
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(if (isExpanded) "hide thinking" else "show thinking")
            }
        }
    }
}
```

### Streaming Updates

The thinking text streams in via `MessageCallback.onMessage()`. Each time the callback fires, the UI receives the accumulated thinking content via `message.channels["thought"]`. The `LaunchedEffect(thinkingText)` pattern will trigger recomposition as the string grows, causing the `Text` composable to redraw with new content.

### Ordering in ChatBubble

The thinking bubble should appear **above** the main text response:

```kotlin
Column(horizontalAlignment = Alignment.Start) {
    // Thinking bubble first (if thinking text exists)
    if (message.thinkingText.isNotEmpty()) {
        ThinkingBubble(
            thinkingText = message.thinkingText,
            modifier = Modifier.padding(bottom = 4.dp)
        )
    }

    // Then the actual response
    Box(
        modifier = Modifier
            .widthIn(max = 280.dp)
            .background(...)
            .padding(12.dp)
    ) {
        MarkdownText(...)
    }
}
```

---

## Integration with LlmChatModelHelper

**In LlmChatModelHelper.sendMessage()**, the thinking channel is already being logged but not exposed to UI. To expose it, the `onToken` callback signature would need to include thinking content, or a separate callback would be needed.

**Current approach**: The thinking channel in `onMessage()` could update a separate `thinkingText` state in the message list. The `onToken` callback currently receives only `message.toString()`, so the thinking content would need to be threaded through differently.

**Alternative (simpler)**: Pass thinking content through `extraContext` or add a second callback parameter. The cleanest approach is adding an `onThinkingToken: (String) -> Unit` parameter to `sendMessage()` that fires when `message.channels["thought"]` has content.

---

## Sources

- Compose Animation: `androidx.compose.animation` (in BOM 2024.12.01)
- AnimatedVisibility: standard Compose API, no additional import needed
- Material3: already in project
- Existing `ChatBubble` pattern: MainActivity.kt lines 1409-1467
- Thinking channel access: LlmChatModelHelper.kt lines 328-331
# Domain Pitfalls: Streaming Thinking Display in Android Compose

**Domain:** Android Compose chat UI with streaming LLM thinking/reasoning output
**Researched:** 2026-05-04
**Confidence:** MEDIUM (based on codebase analysis + Compose/streaming best practices)

---

## Critical Pitfalls

### Pitfall 1: Background Thread UI Updates

**What goes wrong:** LiteRT-LM `MessageCallback.onMessage()` runs on a background thread. Updating Compose state directly causes crashes or silent failures.

**Why it happens:** The `onMessage` callback in `LlmChatModelHelper.sendMessage()` is invoked from LiteRT-LM's internal executor, not the main thread.

**Consequences:**
- App crash with `CalledFromWrongThreadException`
- Silent failures where thinking text never appears
- Race conditions in message list

**Prevention:**
```kotlin
// CORRECT: Always post to main thread
mainHandler.post {
    thinkingText = thinkingText + newToken
}
```

The codebase already has `mainHandler.post {}` pattern in `LlmChatModelHelper.kt` for `onToken` callbacks. Ensure the thinking channel extraction also uses this pattern.

**Detection:** Add logging in callback - if logs appear but UI does not update, threading is the issue.

---

### Pitfall 2: Recomposition Thrashing with Streaming Tokens

**What goes wrong:** Each token from the thinking channel triggers a full recomposition of the message list, causing UI stutter or jank.

**Why it happens:** Naive state accumulation: `thinkingText += token` creates a new `String` reference each time, causing `mutableStateOf<String>` to trigger recomposition for every token (potentially hundreds per second).

**Consequences:**
- UI jank/stutter during thinking display
- Battery drain from excessive recompositions
- 60fps drop on lower-end devices

**Prevention:**
1. Use `remember { mutableStateOf("") }` for accumulation but batch updates
2. Consider `produceState` with `consumeAsState().value` for more control
3. Add a small debounce/throttle if tokens arrive too rapidly (though Gemma tokens are typically slow enough)
4. Keep thinking bubble in a separate state from the message list during streaming

```kotlin
// RECOMMENDED: Separate streaming state from message list
var streamingThinking by remember { mutableStateOf("") }

// On token arrival:
mainHandler.post {
    streamingThinking += token  // This only recomposes the thinking bubble
}
```

---

### Pitfall 3: Scroll Position Jump When Streaming Starts

**What goes wrong:** When thinking bubble appears or grows, the `LazyColumn` scroll position jumps, disrupting user experience.

**Why it happens:** `LazyColumn` recalculates layout when new items are added or content size changes. If not handled, the scroll position anchor is lost.

**Consequences:**
- User loses their scroll position
- UX feels "jerky" when thinking starts
- May scroll past the thinking bubble entirely

**Prevention:**
1. Use `LazyColumn` with `reverseLayout = false` and anchor at bottom
2. Call `listState.animateScrollToItem(index)` after content updates instead of automatic scroll
3. Use `LaunchedEffect` to smooth-scroll only when necessary (not on every token)

```kotlin
LaunchedEffect(streamingThinking.length) {
    if (streamingThinking.isNotEmpty()) {
        // Smooth scroll to bottom only if already near bottom
        if (listState.firstVisibleItemIndex > messages.size - 3) {
            listState.animateScrollToItem(messages.size + 1)
        }
    }
}
```

---

### Pitfall 4: Mixing Thinking Content with Main Response

**What goes wrong:** Thinking content from `message.channels["thought"]` gets concatenated with or replaces the actual response, showing reasoning in the wrong place.

**Why it happens:** The `onMessage` callback receives both channel content and main `message.toString()`. Without careful handling, both get displayed.

**Consequences:**
- Thinking text appears in the main response bubble
- Duplicate content
- Confusing UX where reasoning shows as the answer

**Prevention:**
1. In `LlmChatModelHelper.onMessage()`, extract thinking from `message.channels["thought"]` separately
2. Pass thinking content via a dedicated callback, not mixed with main response
3. Ensure the thinking bubble disappears when the main response starts arriving

```kotlin
// In callback - the codebase already logs this, needs UI integration:
val thinkingContent = message.channels["thought"]
if (!thinkingContent.isNullOrEmpty()) {
    AppLogger.d(TAG, "[THOUGHT-CHANNEL] $thinkingContent")
    // TODO: route to thinking bubble state, not main response
}
```

---

### Pitfall 5: Memory Leak from Uncleaned Callbacks

**What goes wrong:** When user navigates away or restarts conversation during streaming, the old callback remains referenced, causing memory leaks.

**Why it happens:** The `MessageCallback` anonymous class holds a reference to the composable's scope. If conversation is reset or activity destroyed, the callback chain retains memory.

**Consequences:**
- Memory grows over multiple conversations
- OutOfMemoryError after extended use
- Stale UI state from recycled callbacks

**Prevention:**
1. Cancel/close conversation before starting a new one
2. Use `DisposableEffect` to clean up on composable leave
3. Set callback references to `null` when `onDone` or `onError` is called

```kotlin
DisposableEffect(Unit) {
    onDispose {
        // Clean up any in-flight streaming
        streamingThinking = ""
    }
}
```

---

## Moderate Pitfalls

### Pitfall 6: Collapse State Not Persisting Across Recompositions

**What goes wrong:** User collapses the thinking bubble, but it re-expands on the next recomposition.

**Why it happens:** Using `remember { mutableStateOf(false) }` for collapse state without proper key/traversal.

**Prevention:**
```kotlin
var isThinkingCollapsed by remember { mutableStateOf(false) }

// Use a stable key tied to this specific thinking session
key(thinkingSessionId) {
    ThinkingBubble(
        content = streamingThinking,
        isCollapsed = isThinkingCollapsed,
        onToggleCollapse = { isThinkingCollapsed = !isThinkingCollapsed }
    )
}
```

---

### Pitfall 7: 4-Line Limit Enforcement Causes Truncation Mid-Word

**What goes wrong:** Simply truncating at 4 lines cuts words in half, making thinking unreadable.

**Why it happens:** Naive substring or `maxLines = 4` without word-boundary awareness.

**Prevention:**
```kotlin
// APPROACH 1: Let Compose handle it naturally with maxLines + ellipsis
Text(
    text = thinkingText,
    maxLines = 4,
    overflow = TextOverflow.Ellipsis  // Shows "..." at end
)

// APPROACH 2: If manual control needed, find word boundary
fun truncateToWordBoundary(text: String, maxChars: Int): String {
    if (text.length <= maxChars) return text
    val truncated = text.substring(0, maxChars)
    val lastSpace = truncated.lastIndexOf(' ')
    return if (lastSpace > maxChars * 0.7) truncated.substring(0, lastSpace) else truncated
}
```

---

### Pitfall 8: Thinking Bubble Flicker on Response Start

**What goes wrong:** When main response starts arriving, thinking bubble disappears with a flicker/jump as the layout recalculates.

**Why it happens:** Thinking bubble and response message are separate items. When response begins, the layout shifts.

**Prevention:**
1. Keep thinking bubble in a fixed-size container during streaming
2. When response starts, fade out thinking bubble (`AnimatedVisibility`) before removing from list
3. Animate the transition smoothly

```kotlin
AnimatedVisibility(
    visible = streamingThinking.isNotEmpty(),
    exit = fadeOut() + slideOutVertically()
) {
    ThinkingBubble(...)
}
```

---

## Minor Pitfalls

### Pitfall 9: Ignoring `onError` During Streaming

**What goes wrong:** If an error occurs mid-stream, thinking bubble remains stuck on screen.

**Why it happens:** `onError` callback clears state but thinking bubble state is separate.

**Prevention:** Ensure `onError` in `LlmChatModelHelper` clears streaming thinking state.

---

### Pitfall 10: Accessibility - Screen Reader Not Announcing Streaming Updates

**What goes wrong:** Screen readers cannot access thinking content as it streams.

**Why it happens:** Dynamic content updates without `contentDescription` or proper semantics.

**Prevention:**
```kotlin
Text(
    text = thinkingText,
    modifier = Modifier.semantics {
        liveRegion = LiveRegionMode.Polite  // Announce changes
    }
)
```

---

## Phase-Specific Warnings

| Phase Topic | Likely Pitfall | Mitigation |
|-------------|---------------|------------|
| **UI Implementation** | Pitfalls 1, 2, 3 | Ensure mainHandler.post for all state updates, batch tokens, handle scroll |
| **Collapse/Expand Logic** | Pitfalls 6, 7 | Use stable key for collapse state, prefer ellipsis truncation |
| **Thinking-to-Response Transition** | Pitfalls 4, 8 | Separate channel handling from response, animate fade-out |
| **Error Handling** | Pitfall 9 | Ensure streaming state cleared in all completion paths |

---

## Sources

- [Jetpack Compose State Documentation](https://developer.android.com/develop/ui/compose/state)
- [LazyColumn Best Practices](https://developer.android.com/develop/ui/compose/lists)
- [Compose Animation](https://developer.android.com/develop/ui/compose/animation)
- [LiteRT-LM MessageCallback](https://github.com/google-ai-edge/LiteRT-LM) (from codebase analysis)

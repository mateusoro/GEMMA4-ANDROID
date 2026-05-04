# Research Summary: Thinking Bubble UI

**Project:** Gemma4Android
**Milestone:** Thinking Bubble UI
**Synthesized:** 2026-05-04
**Confidence:** HIGH (based on existing codebase patterns + standard Compose APIs)

---

## Executive Summary

The thinking bubble UI is a contained feature with minimal dependencies. No new libraries are required — the existing Compose BOM 2024.12.01 provides all needed animation and UI components. The core work involves: (1) adding a `thinkingText` field to `ChatMessage`, (2) wiring the thinking channel from `LlmChatModelHelper` to the UI via a new `onThinking` callback, and (3) creating a `ThinkingBubble` composable with streaming support.

The thinking channel is already separated from the response text at the LiteRT-LM level (`message.channels["thought"]` vs `message.toString()`), but currently only the response text is exposed to the UI. The key integration point is extending `sendMessage()` with an `onThinking` callback to route thinking tokens to the UI state.

**Key risks:** threading issues (all UI updates must go through `mainHandler.post`), recomposition thrashing (batch token updates), and scroll position jumps when the thinking bubble appears.

---

## Key Findings

### From STACK.md

| Technology | Decision | Rationale |
|------------|----------|-----------|
| Compose BOM 2024.12.01 | Already present | No new dependencies needed |
| Material3 | Already present | Use for styling |
| `ChatMessage.thinkingText` | Add `String = ""` field | Backward compatible — existing callers use defaults |
| `AnimatedVisibility` | Use for expand/collapse | Standard Compose, no extra imports |
| `onThinking` callback | Add to `sendMessage()` | Nullable parameter for backward compatibility |

### From FEATURES.md

**Table Stakes (MVP):**
- Thinking bubble appears above bot message when thinking channel has content
- Streaming text updates as tokens arrive (real-time feedback)
- Visual distinction: surfaceVariant background, smaller text, "thinking..." label
- Smooth animation on appear/disappear (fade + slide)

**Should-Have (Post-MVP):**
- Expand/collapse toggle (4-line clamp with "show/hide thinking" button)
- Persist thinking bubble after completion (collapsed by default)

**Anti-Features (Never Build):**
- Thinking bubble for ALL messages — only show when `channels["thought"]` has content
- Loading spinner instead of streaming text
- Blocking animation that waits for thinking to finish

### From ARCHITECTURE.md

**Data Model Change:**
```kotlin
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val text: String = "",
    val isUser: Boolean,
    val thinkingText: String = "",  // NEW — nullable, backward compatible
    val throughput: Float = 0f,
    val tokenCount: Int = 0,
    val durationMs: Long = 0L
)
```

**Callback Signature Change:**
```kotlin
fun sendMessage(
    message: String,
    onToken: (String) -> Unit,
    onThinking: ((String) -> Unit)? = null,  // NEW — nullable, backward compatible
    onDone: () -> Unit,
    onError: (Throwable) -> Unit,
    extraContext: Map<String, String> = emptyMap()
)
```

**Build Order:**
1. Add `thinkingText` to `ChatMessage` — data model, backward compatible
2. Add `onThinking` parameter to `LlmChatModelHelper.sendMessage()` — callback wiring
3. Wire thinking channel in `onMessage()` — invoke `onThinking?.invoke(message.channels["thought"])`
4. Create `ThinkingBubble` composable — muted styling, streaming support
5. Update `LazyColumn` item rendering — render `ThinkingBubble` above `ChatBubble` when `!isUser && thinkingText.isNotEmpty()`
6. Test streaming + completion transitions

**Anti-Patterns to Avoid:**
- Blocking UI during thinking (show as it arrives)
- Replacing/hiding thinking when response starts (keep both visible)
- Adding thinking as a separate list item (attach as field on existing bot message)

### From PITFALLS.md

**Critical (Must Address):**
1. **Background thread UI updates** — always use `mainHandler.post {}` for UI state changes
2. **Recomposition thrashing** — batch token updates, avoid `thinkingText += token` per token
3. **Scroll position jump** — handle `LazyColumn` layout recalculation when bubble appears
4. **Mixing thinking with response** — ensure thinking goes to `thinkingText`, not `text`
5. **Memory leak from uncleaned callbacks** — clear streaming state in `onDone`/`onError`/`DisposableEffect`

**Moderate (Address in Phase 2-3):**
6. **Collapse state not persisting** — use stable key for collapse toggle state
7. **4-line truncation mid-word** — use Compose `maxLines = 4` + `TextOverflow.Ellipsis`
8. **Thinking bubble flicker** — animate fade-out with `AnimatedVisibility` when response starts

---

## Implications for Roadmap

Based on combined research, I recommend **3 phases**:

### Phase 1: Foundation (Data Model + Callback Wiring)
**Rationale:** Other work depends on these changes. Must complete before UI implementation.

**Delivers:**
- `ChatMessage.thinkingText` field
- `LlmChatModelHelper.sendMessage()` with `onThinking` callback
- Thinking channel routing in `onMessage()`
- Basic streaming state management in `ChatScreen`

**Pitfalls to avoid:** Pitfall 1 (threading), Pitfall 4 (mixing channels), Pitfall 5 (memory leak)

**Research flag:** Standard pattern — no deeper research needed.

---

### Phase 2: UI Implementation (ThinkingBubble + Integration)
**Rationale:** Self-contained feature work. Can proceed once foundation is complete.

**Delivers:**
- `ThinkingBubble` composable with muted styling
- `LazyColumn` item rendering update (thinking above response)
- Streaming updates wired to `thinkingText`
- Basic collapse at 4 lines with toggle

**Pitfalls to avoid:** Pitfall 2 (recomposition), Pitfall 3 (scroll jump), Pitfall 6 (collapse state), Pitfall 7 (truncation), Pitfall 8 (flicker)

**Research flag:** Standard Compose patterns — no deeper research needed.

---

### Phase 3: Polish (Animation + Edge Cases)
**Rationale:** Refinement work after basic functionality is verified.

**Delivers:**
- Smooth appear/disappear animations (`AnimatedVisibility`)
- Fade-out transition when response starts
- `onError` handling clears thinking state
- Accessibility: `LiveRegionMode.Polite` for screen readers

**Pitfalls to avoid:** Pitfall 9 (ignoring onError), Pitfall 10 (accessibility)

**Research flag:** Standard Compose animation — no deeper research needed.

---

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | HIGH | Based on existing codebase + Compose BOM 2024.12.01 |
| Features | MEDIUM | Established UX patterns; cannot verify current ChatGPT/Claude/Gemini UI |
| Architecture | HIGH | Clear patterns from codebase analysis + standard Compose |
| Pitfalls | MEDIUM | Based on codebase analysis + Compose best practices; threading issues well-understood |

**Overall:** HIGH — confident in direction. The main risks (threading, recomposition) are well-documented with clear prevention strategies.

**Gaps to Address:**
- Cannot verify current thinking UI patterns from competitors (web access restricted)
- Token streaming performance on lower-end devices not tested
- Large thinking content (10K+ chars) edge case not explored

---

## Sources

- STACK.md: build.gradle.kts, LlmChatModelHelper.kt, existing Compose patterns
- FEATURES.md: established AI chat UX patterns (ChatGPT, Claude, Gemini)
- ARCHITECTURE.md: MainActivity.kt (lines 964-981, 1409-1467, 1469-1476), LlmChatModelHelper.kt (lines 296-337)
- PITFALLS.md: Jetpack Compose documentation, LiteRT-LM MessageCallback analysis
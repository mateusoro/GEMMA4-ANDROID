---
slug: thinking-vs-toolcalling
status: fixing
trigger: "thinking mode causes model hang - thinking ON + tool calling = infinite reasoning loop"
created: 2026-05-04
updated: 2026-05-04
---

# Debug Session: thinking-vs-toolcalling

## Symptoms

| Mode | Result |
|------|--------|
| thinking OFF + empty channels | ✅ 34 chars, ~2s |
| thinking ON + listOf(thinkingChannel) | ❌ hang, infinite thought tokens, onDone never called |

## Root Cause (INTERIM)

**We pass `enable_thinking` at WRONG level:**
- Ours: `extraContext` in `ConversationConfig` at `initialize()` (conversation-level)
- Gallery: `extraContext` in `sendMessageAsync()` (per-message level)

**We pass explicit `Channel(...)` in ConversationConfig:**
- Ours: `channels = listOf(thinkingChannel)` — overrides model metadata
- Gallery: `channels = null` — uses model's built-in channel definitions

## Research Findings

### Gallery Pattern (CORRETO):
```kotlin
// 1. ConversationConfig — NO channels, no extraContext for thinking
ConversationConfig(
    systemInstruction = sysInstruction,
    tools = tools,
    samplerConfig = samplerConfig,
    automaticToolCalling = true,
    channels = null,           // ← uses model metadata
    extraContext = emptyMap()    // ← NO enable_thinking here
)

// 2. sendMessageAsync — enable_thinking PER MESSAGE
conversation.sendMessageAsync(
    Contents.of(contents),
    messageCallback,
    mapOf("enable_thinking" to "true")  // ← string "true", not boolean
)
```

### Our Pattern (ERRADO):
```kotlin
// 1. initialize — passes channels + extraContext at CONVERSATION level
LlmChatModelHelper.initialize(
    ...
    listOf(thinkingChannel),              // ← WRONG: explicit Channel
    mapOf("enable_thinking" to true)       // ← WRONG: at initialize level
)

// 2. sendMessageAsync — no extraContext
conversation.sendMessageAsync(contents, callback)  // ← missing extraContext
```

## Key Findings from Research

1. `enable_thinking` is a Jinja template variable — `{% if enable_thinking %}`
2. Per-message `extraContext` MERGES and OVERWRITES conversation-level `extraContext`
3. `channels = null` in ConversationConfig → uses model metadata defaults
4. Gallery passes `"true"` (string) not `true` (boolean) — template does string comparison
5. `Message.channels["thought"]` carries thinking content token-by-token
6. Thinking channel markers: `<|channel>thought\n` (start), `<channel|>` (end)

## Fix Plan

1. Remove `listOf(thinkingChannel)` from `ConversationConfig` — use `null`
2. Pass `enable_thinking` via `sendMessageAsync(contents, callback, extraContext)` not via initialize
3. Use string `"true"` not boolean `true`
4. Read thinking from `message.channels["thought"]` in `onMessage`

## Reference Links

- [Gemma 4 Prompt Formatting](https://ai.google.dev/gemma/docs/core/prompt-formatting-gemma4)
- [LiteRT-LM getting_started.md](https://github.com/google-ai-edge/LiteRT-LM/blob/main/docs/api/kotlin/getting_started.md)
- [Gallery LlmChatViewModel](https://github.com/google-ai-edge/gallery/blob/main/Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/llmchat/LlmChatViewModel.kt) lines 210-226
- [LiteRT-LM Config.kt Channel](https://github.com/google-ai-edge/LiteRT-LM/blob/main/kotlin/java/com/google/ai/edge/litertlm/Config.kt)
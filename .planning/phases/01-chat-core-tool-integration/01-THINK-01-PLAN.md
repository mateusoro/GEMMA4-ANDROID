---
phase: 01
plan: THINK-01
wave: 2
depends_on: [CHAT-01]
requirements_addressed: [CHAT-04]
autonomous: false
---

# Plan: Thinking Mode Display — Show Model's Internal Reasoning

## Objective
Parse Channel("thought") output from LiteRT-LM and display thinking bubbles before the model's response.

## Files Modified
- `app/src/main/java/com/gemma/gpuchat/MainActivity.kt`
- `app/src/main/java/com/gemma/gpuchat/LlmChatModelHelper.kt`

## must_haves
- Thinking content appears in separate bubble before main response
- Thinking bubble is visually distinct from user and bot messages
- Only shows when model actually produces thinking content

## Context
From AGENTS.md learned patterns:
- `Channel("thought", "<|channel>thought\n", "<channel|>")` — thinking channel
- Thinking arrives via `Message.channels["thought"]` (token-by-token before main response)
- Log with `[THOUGHT-CHANNEL]` tag

---

## Task 1: Review LiteRT-LM Thinking Channel API

<read_first>
- `app/src/main/java/com/gemma/gpuchat/LlmChatModelHelper.kt` (onMessage callback)
- Google AI Edge LiteRT-LM documentation (search web for Channel usage)
</read_first>

<acceptance_criteria>
- onMessage callback receives Message with channels map
- "thought" channel contains thinking tokens before main response
- Main content arrives via message.toString() after thinking completes
</acceptance_criteria>

<action>
Search for LiteRT-LM Channel thinking mode documentation online.
Understand:
1. How to register Channel("thought", ...) with LiteRT
2. How onMessage receives channel data (is it a separate callback parameter?)
3. How to distinguish thinking tokens from main response tokens

Look at existing LlmChatModelHelper code to see if channels are already configured.
</action>

---

## Task 2: Register Thinking Channel with LiteRT

<read_first>
- `app/src/main/java/com/gemma/gpuchat/LlmChatModelHelper.kt` (EngineConfig or ConversationConfig setup)
</read_first>

<acceptance_criteria>
- channels list includes Channel("thought", "<|channel>thought\n", "<channel|>")
- extraContext contains enable_thinking: true
- thinking content flows through onMessage callback
</acceptance_criteria>

<action>
In LlmChatModelHelper, find where ConversationConfig or EngineConfig is built for sendMessage.
Add channel configuration:

```kotlin
val channels = listOf(
    Channel("thought", "<|channel>thought\n", "<channel|>")
)
val extraContext = mapOf("enable_thinking" to true)
// pass to sendMessage or conversation config
```

Check if this requires changes to how sendMessage is called.
</action>

---

## Task 3: Parse Thinking Content in onMessage Callback

<read_first>
- `app/src/main/java/com/gemma/gpuchat/LlmChatModelHelper.kt` (onMessage callback signature)
</read_first>

<acceptance_criteria>
- onMessage receives Message with channels["thought"] content
- App logs thinking with [THOUGHT-CHANNEL] tag
- Streaming thinking updates UI in real-time
</acceptance_criteria>

<action>
Modify onMessage callback to:
1. Check if Message has channels map with "thought" key
2. If yes, extract thinking text and stream to UI via mainHandler.post
3. Log each thinking update with AppLogger.d(TAG, "[THOUGHT-CHANNEL] $thinking")

Add a new mutableState for thinking buffer in MainActivity:
```kotlin
var thinkingText = mutableStateOf("")
```

Update UI to show thinking bubble when thinkingText is not empty.
</action>

---

## Task 4: Add Thinking Bubble UI in MainActivity

<read_first>
- `app/src/main/java/com/gemma/gpuchat/MainActivity.kt` (message item composable)
</read_first>

<acceptance_criteria>
- Thinking bubble appears ABOVE bot message when thinking
- Visually distinct: gray background, smaller text, "thinking..." label
- Thinking bubble disappears when main response starts
- No thinking bubble shown when model doesn't think
</acceptance_criteria>

<action>
In the LazyColumn message list, add a message item type for thinking:
```kotlin
if (thinkingText.value.isNotEmpty()) {
    MessageItem(
        role = "thinking",
        content = thinkingText.value,
        timestamp = System.currentTimeMillis()
    )
}
```

Create a distinct visual style for thinking bubble:
- Background: MaterialTheme.colorScheme.surfaceVariant
- Text style: MaterialTheme.typography.bodySmall, italic
- Label: "thinking..." in gray

Only show thinking bubble when thinkingText.value.isNotEmpty().
</action>

---

## Task 5: Verify Thinking Mode End-to-End

<read_first>
- LiteRT-LM Gemma model thinking documentation
</read_first>

<acceptance_criteria>
- When model thinks, thinking bubble appears before response
- Thinking text streams in token-by-token
- Response appears after thinking completes
- No thinking bubble when model responds directly (no thinking)
</acceptance_criteria>

<action>
1. Build and deploy to device
2. Ask a question that typically triggers thinking (complex reasoning)
3. Verify thinking bubble appears before response
4. Ask a simple factual question
5. Verify response comes without thinking bubble (or very brief)

Check logcat for [THOUGHT-CHANNEL] messages to confirm thinking is being received.
</action>

---

## Verification
1. Ask "What is 2+2? Why?" → thinking bubble appears → response follows
2. Ask "What time is it?" → may or may not show thinking
3. Logcat shows [THOUGHT-CHANNEL] tags for thinking content
4. Thinking bubble visually distinct from user/bot messages
# Research Report: `enable_thinking` in LiteRT-LM Kotlin API — Full Mechanics

---

## 1. How `enable_thinking` Works — Jinja Template Variable

**Source: [LiteRT-LM Kotlin getting_started.md](https://github.com/google-ai-edge/LiteRT-LM/blob/main/docs/api/kotlin/getting_started.md) + C++ conversation.cc**

`extraContext` is a `Map<String, Any>` that is merged into `PromptTemplateInput.extra_context` — the Jinja template context. The template reads it as a variable:

```kotlin
// From LiteRT-LM Kotlin docs:
val extraContext = mapOf(
    "user_name" to "Alice",
    "enable_thinking" to true   // <-- boolean, not string!
)
conversation.sendMessageAsync("Hello!", extraContext = extraContext)
    .collect { ... }

// In the Jinja template:
// {{ enable_thinking }} or {% if enable_thinking %}...{% endif %}
```

The docs explicitly say:
> "These variables are used within the Jinja-style prompt templates, e.g., `{{ user_name }}` or `{% if enable_thinking %}`."

---

## 2. Two Levels: Conversation-Level vs Per-Message

### Conversation-Level (Preface)
**Source: [C++ `conversation.cc`](https://github.com/google-ai-edge/LiteRT-LM/blob/7e4906df/runtime/conversation/conversation.cc) + [CPP docs](https://github.com/google-ai-edge/LiteRT-LM/blob/main/docs/api/cpp/conversation.md)**

```cpp
// C++ example from docs:
Preface preface = JsonPreface({
  .messages = { {"role", "system"}, {"content", "You are a helpful assistant."} },
  .tools = { ... },
  .extra_context = {
    {"enable_thinking": false}  // <-- conversation-level, applies to ALL messages
  }
});
```

In Kotlin, this would be `ConversationConfig(extraContext = mapOf("enable_thinking" to false))` — set once at conversation creation.

### Per-Message (override)
**Source: [C++ `conversation.cc`](https://github.com/google-ai-edge/LiteRT-LM/blob/7e4906df/runtime/conversation/conversation.cc))**

```cpp
// Message-level extra_context MERGES INTO and OVERWRITES preface-level:
// "Merge extra context for the message into the extra context provided in the
//  preface. Existing keys will be overwritten."
if (optional_args.extra_context.has_value()) {
  for (const auto& [key, value] : optional_args.extra_context->items()) {
    old_tmpl_input.extra_context[key] = value;  // overwrites preface keys
  }
}
```

So `sendMessageAsync`'s `extraContext` parameter **overrides** the conversation-level (Preface) `extraContext`.

---

## 3. The Critical Type: Boolean, Not String

**Gallery bug found:** Gallery passes `"true"` (String), not `true` (Boolean):

```kotlin
// LlmChatViewModel.kt line 212 — BUG: passing String, not boolean
val extraContext = if (enableThinking) mapOf("enable_thinking" to "true") else null
```

This depends on whether the Jinja template does `"true"` truthiness or strict `true` boolean comparison. The LiteRT-LM docs use `true` (boolean). The C++ code passes `nlohmann::json` value directly, so both `true` (bool) and `"true"` (string) would be distinct JSON values — only `true` (boolean) would be truthy in Jinja `{% if enable_thinking %}`.

However, Gallery has been working with this setup, so either:
1. The Gemma-4 `.litertlm` model template does string-aware comparison, or
2. The model metadata handles `enable_thinking` separately from the template

---

## 4. How Gallery Currently Does It (Working Pattern)

**`LlmChatViewModel.kt` lines 210–226:**
```kotlin
val enableThinking =
    allowThinking &&
        model.getBooleanConfigValue(key = ConfigKeys.ENABLE_THINKING, defaultValue = false)
val extraContext = if (enableThinking) mapOf("enable_thinking" to "true") else null

model.runtimeHelper.runInference(
    model = model,
    input = input,
    images = images,
    audioClips = audioClips,
    resultListener = resultListener,
    cleanUpListener = cleanUpListener,
    onError = errorListener,
    coroutineScope = viewModelScope,
    extraContext = extraContext,   // null when off, {"enable_thinking": "true"} when on
)
```

**`LlmChatModelHelper.runInference()` lines 243–260:**
```kotlin
conversation.sendMessageAsync(
    Contents.of(contents),
    object : MessageCallback {
        override fun onMessage(message: Message) {
            resultListener(message.toString(), false, message.channels["thought"])
        }
        // ...
    },
    extraContext ?: emptyMap(),  // forwarded to sendMessageAsync
)
```

**Key observation:** Gallery passes `null` (meaning "empty map") when thinking is disabled. This is passed per-message to `sendMessageAsync`, NOT at conversation creation. Each message can independently have thinking enabled or disabled.

---

## 5. ConversationConfig.extraContext vs sendMessageAsync.extraContext

| Scope | How to set | Effect |
|-------|-----------|--------|
| **Conversation-level** | `ConversationConfig(extraContext = mapOf("enable_thinking" to true))` at `createConversation()` | Applies to ALL messages; permanent for conversation lifetime |
| **Message-level** | `sendMessageAsync(content, callback, extraContext = mapOf("enable_thinking" to true))` | Per-message override; can vary message to message |

**Which to use for thinking toggle:**
- **Per-message (Gallery approach)** is better for a UI toggle — users can enable/disable per message
- **Conversation-level** would set it once, but then you'd need to recreate the conversation to change it

The C++ code confirms message-level **merges and overwrites** conversation-level:
> "Existing keys will be overwritten."

---

## 6. Channel Parsing Is Automatic — No Explicit Channel Config Needed

The `Channel` struct in `Config.kt` exists for explicit channel parsing, but for Gemma-4 thinking, the model metadata already includes channel definitions. The key insight from `io_types.h`:

```cpp
// Channel struct:
struct Channel {
  std::string channel_name;  // "thought"
  std::string start;         // "<|channel>thought"
  std::string end;           // "<channel|>"
};
```

When `channels = null` in `ConversationConfig`, LiteRT-LM uses the default channel config from `LlmMetadata` baked into the `.litertlm` file. Gallery doesn't pass explicit `Channel(...)` for this reason.

---

## Complete Checkpoint

**Done:**
- Confirmed `enable_thinking` is a Jinja template variable — template uses `{% if enable_thinking %}` to inject `<|think|>`
- Confirmed two-level override: `sendMessageAsync`'s `extraContext` OVERWRITES `ConversationConfig`'s `extraContext` for the same keys
- Confirmed Gallery passes `extraContext` per-message via `sendMessageAsync`, NOT at conversation creation
- Confirmed `MessageCallback.onMessage` returns `Message` where `message.channels["thought"]` carries the thinking content as a `String`
- Confirmed `ResultListener` signature: `(content: String, isDone: Boolean, thought: String?)`
- Confirmed `Channel("thought", "<|channel>thought\n", "<channel|>")` is the canonical channel definition for Gemma-4
- Confirmed when `channels = null` in `ConversationConfig`, model metadata defaults are used
- Identified potential type mismatch: Gallery passes `"true"` (String) but docs use `true` (Boolean)

**Missing:**
- Whether `"true"` (string) works as truthy in the Gemma-4 Jinja template, or if Gallery has a separate mechanism
- Exact Gemma-4-E2B-IT.litertlm model metadata channel definitions — confirmed working but not inspected directly
- Whether the `enable_thinking` Jinja variable name is consistent across all Gemma-4 model variants

**Need Internet Research:**
- The Gemma-4-E2B-IT `.litertlm` model's built-in Jinja template to confirm `enable_thinking` variable name and truthiness behavior
- Whether `filter_channel_content_from_kv_cache` is relevant for thinking tokens

**Suggested Next Action:**
Use Gallery's proven approach: pass `extraContext = mapOf("enable_thinking" to "true")` per-message to `sendMessageAsync`. For the Channel struct, use `Channel("thought", "<|channel>thought\n", "<channel|>")` if explicitly configuring channels. For reading thinking content, use `message.channels["thought"]` in the `onMessage` callback. The `"true"` string vs `true` boolean question should be verified against the actual model template behavior.
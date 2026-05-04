---
name: ml-engineer
description: Owns all LiteRT-LM integration, tool calling system, model configuration, and Gemma-4-E2B-IT-specific behaviors. Triggers when: modifying LlmChatModelHelper, adding new tools to AgentTools, changing model parameters (temperature, topK, topP, maxTokens), implementing tool calling, configuring system instructions, handling thinking mode, or debugging model behavior. Also handles: WAV audio encoding, model warmup issues, conversation reset, tool result formatting.
---

# ML Engineer Skill

Owns LiteRT-LM integration, tool calling, and Gemma-4-E2B-IT-specific behaviors.

## Core Components

### LlmChatModelHelper
Singleton object managing Engine, Conversation, and all model interactions.

**Key methods:**
- `initialize(context, modelPath, params, agentTools, tools, systemInstruction, channels, extraContext, onProgress)`
- `sendMessage(message, onToken, onDone, onError, extraContext)`
- `sendAudioMessage(audioBytes, onToken, onDone, onError)`
- `reload(params, systemInstruction, onProgress)` — **only for param changes, NOT settings changes**
- `resetConversation()` — closes old conversation, creates new one
- `release()` — cleanup on activity destroy

**ConversationConfig (use always):**
```kotlin
val convConfig = ConversationConfig(
    samplerConfig = SamplerConfig(topK, topP, temperature),
    tools = currentTools,
    systemInstruction = currentSystemInstruction,
    channels = currentChannels,
    extraContext = currentExtraContext ?: emptyMap(),
    automaticToolCalling = false  // Manual mode
)
```

### AgentTools
ToolSet implementation with @Tool-annotated methods.

**Current tools:**
- `showLocationOnMap(location: String)` — opens maps intent
- `createCalendarEvent(datetime: String, title: String)` — creates calendar event
- `listWorkspace()` — lists files in workspace
- `listMarkdown()` — lists markdown files
- `readWorkspaceFile(filename: String)` — reads file (strips markdown/documents/ prefix)
- `saveMarkdownFile(filename: String, content: String)` — saves markdown
- `getDeviceInfo()` — returns datetime, memory, model size

**Tool registration:**
```kotlin
val agentToolsInstance = AgentTools.create(context)
val agentTools = listOf(tool(agentToolsInstance))
// Pass to initialize()
```

### Manual Tool Calling Flow
```kotlin
// 1. Model sends tool call in MessageCallback.onMessage
if (message.toolCalls.isNotEmpty()) {
    for (toolCall in message.toolCalls) {
        val result = executeToolCall(toolCall.name, toolCall.arguments)
        sendToolResult(toolCall.name, result, onToken, onDone, onError)
        return
    }
}

// 2. sendToolResult wraps result as ToolResponse and sends back
val resultJson = mapToJson(result)
val content = Content.ToolResponse(toolName, resultJson)
val toolMessage = Message.tool(Contents.of(content))
conversation?.sendMessageAsync(toolMessage, callback)
```

### System Instruction (CRITICAL)
```kotlin
// WRONG — model ignores this
val sysInstr = Contents.of("system prompt string")

// CORRECT — Gallery pattern
val sysInstr = Contents.of(
    listOf(
        Content.Text("You are a model that can do function calling..."),
        Content.Text("Current date: $now")
    )
)

// Also: prepend to user message as workaround
val fullMessage = "$sysInstrText\n\nUser: $message"
conversation?.sendMessageAsync(Contents.of(fullMessage), ...)
```

### Thinking Mode
```kotlin
// Per-message extraContext enables thinking channel
LlmChatModelHelper.sendMessage(
    message = text,
    onToken = {...},
    onDone = {...},
    onError = {...},
    extraContext = mapOf("enable_thinking" to "true")
)

// Thinking channel config
Channel(
    channelName = "thought",
    start = "<|channel>thought\n",
    end = "<channel|>"
)
```

### WAV Audio Encoding (must have fact chunk)
```kotlin
// 56-byte header: RIFF+fileSize, WAVE, fmt (16 bytes, PCM), fact (4 bytes numSamples), data
// See LlmChatModelHelper.wrapAudioInWav() — do not send raw PCM
```

## Critical Constraints
- System prompt MUST be English starting with "You can do function call."
- `ConversationConfig.systemInstruction` is IGNORED — prepend to user message
- `onSettingsChange` must NOT call `reload()` — use `saveSettings()` only
- Model needs warmup — first inference may return 0 chars after restart
- Log reading: `adb shell "run-as com.gemma.gpuchat cat files/gemma_startup.nlog"`

## Debugging Model Issues
1. Check `files/gemma_startup.nlog` for initialization logs
2. Check `[TOOL]` and `[TOOL-RESPONSE]` tags for tool calling
3. Check `[THOUGHT-CHANNEL]` for thinking output
4. First inference failure: restart app and retry (model warmup issue)

## Adding New Tools
1. Add `@Tool` method to `AgentTools.kt`
2. Implement: validate inputs, execute, return `Map<String, String>`
3. Register tool in MainActivity/LlmChatModelHelper initialization
4. Add test case in `runAgentToolsTests()` or `runToolCallingIntegrationTests()`

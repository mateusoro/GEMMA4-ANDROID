---
phase: 01
plan: CHAT-01
wave: 1
depends_on: []
requirements_addressed: [CHAT-01, CHAT-02, CHAT-03]
autonomous: false
---

# Plan: Chat Core — Message Send/Receive + History Persistence

## Objective
Wire message sending to LiteRT engine, implement JSON file persistence for chat history, and add delete/clear functionality.

## Files Modified
- `app/src/main/java/com/gemma/gpuchat/MainActivity.kt` (message sending UI)
- `app/src/main/java/com/gemma/gpuchat/ChatHistoryManager.kt` (JSON persistence)
- `app/src/main/java/com/gemma/gpuchat/LlmChatModelHelper.kt` (sendMessage integration)

## must_haves
- User can send text message and receive model response
- Messages persist across app restarts
- User can delete individual messages and clear all history

---

## Task 1: Review Current Implementation

<read_first>
- `app/src/main/java/com/gemma/gpuchat/MainActivity.kt` (lines 1-200 for send button, lines 800-1200 for message list)
- `app/src/main/java/com/gemma/gpuchat/ChatHistoryManager.kt` (existing implementation)
- `app/src/main/java/com/gemma/gpuchat/LlmChatModelHelper.kt` (sendMessage method)
</read_first>

<acceptance_criteria>
- MainActivity.kt contains send button with onClick handler (grep: "sendMessage" or "onSend")
- ChatHistoryManager.kt has save() and load() methods
- LlmChatModelHelper.kt has sendMessage() that accepts Contents
</acceptance_criteria>

<action>
Read the three files above to understand current implementation state.
</action>

---

## Task 2: Wire Send Button to LiteRT

<read_first>
- `app/src/main/java/com/gemma/gpuchat/MainActivity.kt` (find the send button and input field)
</read_first>

<acceptance_criteria>
- `onSend()` function reads text from OutlinedTextField
- Calls `LlmChatModelHelper.sendMessage()` with user content
- Clears input field after send
- Adds user message to chat list immediately (optimistic UI)
</acceptance_criteria>

<action>
Find the send button (`IconButton` with send/mic icon) and text input (`OutlinedTextField`) in MainActivity.kt. Create/modify `onSend()` lambda that:
1. Reads text from the mutableStateOf String (input text)
2. Calls `LlmChatModelHelper.sendMessage(Contents.of(userText))`
3. Sets input text to ""
4. Adds user message to messages list

Ensure mainHandler.post is used for UI updates from callbacks.
</action>

---

## Task 3: Wire Token-by-Token Response to UI

<read_first>
- `app/src/main/java/com/gemma/gpuchat/LlmChatModelHelper.kt` (onMessage callback)
</read_first>

<acceptance_criteria>
- `sendMessage()` registers an `onMessage` callback that receives tokens
- Tokens are appended to a mutable State in MainActivity (botMessage mutableStateOf)
- UI updates happen on main thread via mainHandler.post
- LazyColumn scrolls to bottom when new content arrives
</acceptance_criteria>

<action>
In LlmChatModelHelper.sendMessage(), ensure the onMessage callback:
1. Appends each token to `currentBotMessage` String
2. Uses `mainHandler.post { /* update UI */ }` to update composable state
3. On completion (onFinish), saves complete message to ChatHistoryManager

In MainActivity:
1. Have a `var botMessageBuffer = mutableStateOf("")` for streaming tokens
2. On each callback update, show streaming text
3. On finish, convert buffer to permanent MessageItem and add to messages list
</action>

---

## Task 4: Implement JSON Persistence in ChatHistoryManager

<read_first>
- `app/src/main/java/com/gemma/gpuchat/ChatHistoryManager.kt` (existing)
</read_first>

<acceptance_criteria>
- `save(messages: List<MessageItem>)` writes JSON to file in app's filesDir
- `load(): List<MessageItem>` reads JSON file on startup
- JSON format: `[{"role":"user|model","content":"...","timestamp":123456789}]`
- File path: `{context.filesDir}/chat_history.json`
- Handles empty/missing file gracefully (returns empty list)
</acceptance_criteria>

<action>
Review existing ChatHistoryManager. Verify it:
1. Has `save(messages: List<MessageItem>)` — if not, add it
2. Has `load(): List<MessageItem>` — if not, add it
3. File is in `context.filesDir` not getExternalFilesDir
4. Uses Gson or manual JSON parsing

If ChatHistoryManager doesn't exist, create it with:
- `save()`: JSON serialize list, write to file
- `load()`: Read file, JSON deserialize, return list
- Handle exceptions with empty list return
</action>

---

## Task 5: Wire History Load on Startup

<read_first>
- `app/src/main/java/com/gemma/gpuchat/MainActivity.kt` (onCreate)
</read_first>

<acceptance_criteria>
- onCreate loads chat history via ChatHistoryManager.load()
- Loaded messages appear in chat list on startup
- No crash if file doesn't exist (empty list)
</acceptance_criteria>

<action>
In MainActivity's onCreate (or LaunchedEffect), call:
```kotlin
LaunchedEffect(Unit) {
    val history = ChatHistoryManager.load(context)
    messages = history.map { /* convert to MessageItem */ }
}
```
</action>

---

## Task 6: Add Delete Message (Single)

<read_first>
- `app/src/main/java/com/gemma/gpuchat/MainActivity.kt` (message item display)
</read_first>

<acceptance_criteria>
- Long-press on message shows context menu with "Delete"
- Tap "Delete" → removes message from UI list AND from ChatHistoryManager
- Other messages remain
</acceptance_criteria>

<action>
1. Wrap message item in `Modifier.clickable` with `Modifier.contextMenu` or custom long-press handling
2. Show AlertDialog or context menu with "Delete" option
3. On confirm: remove from `messages` list, call `ChatHistoryManager.save(messages)`
</action>

---

## Task 7: Add Clear History in Drawer

<read_first>
- `app/src/main/java/com/gemma/gpuchat/MainActivity.kt` (drawer navigation items)
</read_first>

<acceptance_criteria>
- Drawer has "Clear History" navigation item
- Tap → confirmation dialog ("Clear all chat history?")
- Confirm → clears `messages` list and deletes history file
</acceptance_criteria>

<action>
In drawer NavigationDrawerItem list, add:
```kotlin
NavigationDrawerItem(
    label = { Text("Clear History") },
    icon = { Icon(Icons.Default.Delete, contentDescription = null) },
    selected = false,
    onClick = { /* show confirm dialog */ }
)
```

On confirm:
```kotlin
messages.clear()
ChatHistoryManager.clear(context) // delete file
```
</action>

---

## Task 8: Build and Verify

<read_first>
- `app/build.gradle.kts`
- `app/src/main/AndroidManifest.xml`
</read_first>

<acceptance_criteria>
- `./gradlew assembleDebug` exits with code 0
- APK generated at `app/build/outputs/apk/debug/app-debug.apk`
- No compile errors in output
</acceptance_criteria>

<action>
1. Run `./gradlew assembleDebug` (Windows: `gradlew.bat assembleDebug`)
2. If compile errors, fix them
3. Verify APK exists
4. Deploy to device with `adb install -r app/build/outputs/apk/debug/app-debug.apk`
5. Test: send message, verify response, restart app, verify history
</action>

---

## Verification
1. User types "Hello" → presses send → user message appears in list
2. Model responds → tokens stream in, bot message appears
3. Force-close app via ADB (`adb shell am force-stop com.gemma.gpuchat`)
4. Reopen app → previous messages still visible
5. Long-press message → delete option → message removed
6. Tap "Clear History" in drawer → chat empty → file deleted
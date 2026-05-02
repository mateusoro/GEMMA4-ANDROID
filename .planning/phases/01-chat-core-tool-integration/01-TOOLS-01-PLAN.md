---
phase: 01
plan: TOOLS-01
wave: 1
depends_on: []
requirements_addressed: [TOOL-01, TOOL-02, TOOL-03, TOOL-04, TOOL-05, TOOL-06]
autonomous: false
---

# Plan: Tool Integration — All 5 Tools Working

## Objective
Verify and fix all 5 tool implementations so they actually work when called by the model.

## Files Modified
- `app/src/main/java/com/gemma/gpuchat/AgentTools.kt`
- `app/src/main/java/com/gemma/gpuchat/WorkspaceManager.kt`
- `app/src/main/java/com/gemma/gpuchat/LlmChatModelHelper.kt`

## must_haves
- All 5 tools execute correctly when model calls them
- showLocationOnMap opens map intent
- createCalendarEvent creates calendar entry
- listWorkspace/readWorkspaceFile/saveMarkdownFile work with real files
- getDeviceInfo returns accurate memory/datetime info

---

## Task 1: Review Current AgentTools Implementation

<read_first>
- `app/src/main/java/com/gemma/gpuchat/AgentTools.kt`
</read_first>

<acceptance_criteria>
- AgentTools has 5 tool methods: showLocationOnMap, createCalendarEvent, listWorkspace, readWorkspaceFile, saveMarkdownFile, getDeviceInfo
- Each method returns Map<String, String> with "result" key
- Context is stored as instance field (not method parameter)
</acceptance_criteria>

<action>
Read AgentTools.kt. Note which tools are stubs vs. working implementations. Identify what needs fixing.
</action>

---

## Task 2: Verify showLocationOnMap

<read_first>
- `app/src/main/java/com/gemma/gpuchat/AgentTools.kt` (showLocationOnMap method)
</read_first>

<acceptance_criteria>
- When called with location string, opens Google Maps or browser
- Uses Intent.ACTION_VIEW with geo: URI
- URL-encodes the location string
- Returns success map with action="location_shown"
- Returns error map if context is null
</acceptance_criteria>

<action>
Test the tool manually by calling it from MainActivity after model loads. If it doesn't work:
1. Check if intent resolves to any activity (`ctx.packageManager.resolveActivity`)
2. If no map app, fall back to opening browser with Google Maps URL
3. Ensure URL encoding uses UTF-8
</action>

---

## Task 3: Verify createCalendarEvent

<read_first>
- `app/src/main/java/com/gemma/gpuchat/AgentTools.kt` (createCalendarEvent method)
</read_first>

<acceptance_criteria>
- Parses datetime string (format: YYYY-MM-DDTHH:MM:SS)
- Creates Intent.ACTION_INSERT to CalendarContract.Events
- Sets EXTRA_EVENT_BEGIN_TIME and EXTRA_EVENT_END_TIME (1 hour duration)
- Returns success with action="calendar_event_created"
- Handles parse errors gracefully (logs warning, uses current time)
</acceptance_criteria>

<action>
Review createCalendarEvent. The date parsing uses java.time.LocalDateTime.parse. Verify:
1. Format matches what model will send (ISO-like: "2026-05-03T15:00:00")
2. ZoneId.systemDefault() is used
3. Intent has both BEGIN_TIME and END_TIME (1 hour later)
4. Returns proper result map
</action>

---

## Task 4: Verify listWorkspace and readWorkspaceFile

<read_first>
- `app/src/main/java/com/gemma/gpuchat/AgentTools.kt` (listWorkspace, readWorkspaceFile)
- `app/src/main/java/com/gemma/gpuchat/WorkspaceManager.kt`
</read_first>

<acceptance_criteria>
- listWorkspace calls WorkspaceManager.listWorkspace(ctx)
- readWorkspaceFile strips "markdown/" and "documents/" prefixes
- readWorkspaceFile tries markdown dir first, then documents dir
- Returns error map if file not found
</acceptance_criteria>

<action>
Review WorkspaceManager.listWorkspace and readWorkspaceFile. The AgentTools already calls these. Verify:
1. WorkspaceManager.getMarkdownDir() returns valid path
2. Files are listed correctly
3. readWorkspaceFile handles missing files with error map

Test by creating a test file and verifying listWorkspace returns it.
</action>

---

## Task 5: Verify saveMarkdownFile

<read_first>
- `app/src/main/java/com/gemma/gpuchat/AgentTools.kt` (saveMarkdownFile)
- `app/src/main/java/com/gemma/gpuchat/WorkspaceManager.kt`
</read_first>

<acceptance_criteria>
- Calls WorkspaceManager.saveMarkdown(ctx, filename, content)
- Removes .md suffix if present (WorkspaceManager adds it)
- Returns success with path, or error if save failed
</acceptance_criteria>

<action>
Review WorkspaceManager.saveMarkdown. Verify:
1. Creates file in getMarkdownDir()
2. Writes content to file
3. Returns file path on success, null on failure
4. AgentTools correctly maps null to error result
</action>

---

## Task 6: Verify getDeviceInfo

<read_first>
- `app/src/main/java/com/gemma/gpuchat/AgentTools.kt` (getDeviceInfo)
</read_first>

<acceptance_criteria>
- Calls LlmChatModelHelper.getMemoryUsage() and getSystemMemory(ctx)
- Formats datetime as "yyyy-MM-dd'T'HH:mm:ss"
- Returns day_of_week, app_memory_mb, device_memory_mb, model_size_mb
</acceptance_criteria>

<action>
Review getDeviceInfo. Verify:
1. MemoryInfo fields match (appUsedMb, appTotalMb, modelSizeMb)
2. SystemMemoryInfo fields match (usedMb, totalMb)
3. day_of_week uses correct format pattern ("EEEE")
4. All fields in returned map are Strings
</action>

---

## Task 7: Register Tools with LiteRT Engine

<read_first>
- `app/src/main/java/com/gemma/gpuchat/LlmChatModelHelper.kt` (registerTools or similar)
</read_first>

<acceptance_criteria>
- AgentTools instance is created with AgentTools.create(context)
- Tools are passed to EngineConfig or ConversationConfig
- Model can call tools when automaticToolCalling is enabled
</acceptance_criteria>

<action>
Check LlmChatModelHelper for tool registration:
1. Find where AgentTools is instantiated
2. Find where tools list is passed to EngineConfig
3. Verify `automaticToolCalling = true` is set
4. If tools aren't registered, add:
```kotlin
val tools = listOf(tool(AgentTools.create(context)))
// pass to engine config
```
</action>

---

## Task 8: Test Tool Calling End-to-End

<read_first>
- `app/src/main/java/com/gemma/gpuchat/MainActivity.kt` (tool test in chat)
</read_first>

<acceptance_criteria>
- Ask model "What time is it?" → getDeviceInfo called → datetime shown
- Ask model "Show São Paulo on map" → showLocationOnMap called → map opens
- Ask model "Create meeting at 3pm" → createCalendarEvent called → calendar opens
- Ask model "List my files" → listWorkspace called → file list returned
- Ask model "Read notas.md" (after saving) → readWorkspaceFile called → content returned
</acceptance_criteria>

<action>
Build and deploy to device. Test each tool by asking model explicitly in chat:
1. "What time is it?" — should show device info
2. "Show Tokyo on map" — should open maps
3. "List my workspace files" — should show files
4. "Save a note called test.md with content 'Hello world'" — should save
5. "Read test.md" — should return "Hello world"

Log each tool call via Log.d(TAG, ...) to verify they're being called.
</action>

---

## Verification
1. All 5 tools return success maps
2. Intents open correct apps (map, calendar)
3. File operations work with real files
4. Tool calls appear in logcat with Tool tag
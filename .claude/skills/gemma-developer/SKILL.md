---
name: gemma-developer
description: Implements UI features, state management, and Android-specific integrations for the Gemma4Android chat app. Triggers when: adding new UI components, modifying ChatScreen or SettingsDialog, implementing file picker, audio recording UI, drawer navigation, or any Jetpack Compose work. Also handles: edge-to-edge implementation, Material3 migrations, snackbar/dialog patterns, lazy column state management.
---

# Android Developer Skill

Implements UI features and Android integrations for the Gemma4Android app.

## UI Implementation Patterns

### State Management
```kotlin
var messages by remember { mutableStateOf(listOf<ChatMessage>()) }
var isModelReady by remember { mutableStateOf(false) }
// Use mainHandler.post {} for UI updates from background threads
val mainHandler = remember { Handler(Looper.getMainLooper()) }
mainHandler.post { /* UI update */ }
```

### Edge-to-Edge
```kotlin
// In onCreate
enableEdgeToEdge()
window.isNavigationBarContrastEnforced = false

// In Compose
Modifier.padding(paddingValues)  // from Scaffold topBar/drawer
WindowInsets.safeDrawing  // for drawer content
Modifier.imePadding()     // for keyboard
```

### Material3 Components
- `HorizontalDivider` (not `Divider`)
- `ModalNavigationDrawer` + `ModalDrawerSheet`
- `NavigationDrawerItem` for drawer items
- `TopAppBar` with navigation/drawer icon
- `SnackbarHost` for feedback

### Audio Recording Pattern
```kotlin
val AUDIO_SAMPLE_RATE = 16000
val AUDIO_CHANNEL = AudioFormat.CHANNEL_IN_MONO
val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

// Raw PCM must be wrapped as WAV with fact chunk before sending to model
// See LlmChatModelHelper.wrapAudioInWav() for encoding
```

### PDF Handling
```kotlin
val pdfPickerLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.OpenDocument()
) { uri ->
    // Take persistable permission
    context.contentResolver.takePersistableUriPermission(uri, takeFlags)
    // Process in IO thread
    scope.launch(Dispatchers.IO) {
        val converter = PdfToMarkdownConverter(context)
        val markdown = converter.convert(inputStream)
    }
}
```

## Build & Deploy
```bash
$env:JAVA_HOME = "C:\Program Files\Java\jdk-24"
.\gradlew assembleDebug --no-daemon
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

**Windows/OneDrive fix:** Delete `app/build` before rebuild to avoid `IOException: not a regular file`.

## File Layout
- `MainActivity.kt` — ChatScreen, SettingsDialog, ChatBubble, MarkdownText
- `TestHarnessActivity.kt` — in-app test harness
- Compose UI uses `rememberCoroutineScope()` + `LaunchedEffect` for async
- All UI state in `@Composable` functions; business logic in separate objects

## Adding New Features
1. Implement in appropriate `.kt` file or create new one
2. If UI change, update ChatScreen composable
3. Add test block to TestHarnessActivity
4. Build + install + run TestHarnessActivity
5. Verify all tests pass before committing

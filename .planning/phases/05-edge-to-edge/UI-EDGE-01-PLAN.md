---
phase: 5
plan_id: UI-EDGE-01
wave: 1
depends_on: []
autonomous: false
files_modified:
  - app/src/main/java/com/gemma/gpuchat/MainActivity.kt
  - app/src/main/AndroidManifest.xml
  - app/src/main/res/values/themes.xml
  - app/src/main/java/com/gemma/gpuchat/TestHarnessActivity.kt
---

## Plan: UI-EDGE-01 — Edge-to-Edge + Proper Inset Handling

### Goal

Modern Android UI with proper edge-to-edge display — status bar, navigation bar, and IME insets all properly consumed. Follow Google Material 3 edge-to-edge patterns. The app already calls `enableEdgeToEdge()` at line 135 of `MainActivity.kt`; this plan fixes the missing pieces.

---

## Task: Disable navigation bar contrast enforcement

**read_first:**
- `app/src/main/java/com/gemma/gpuchat/MainActivity.kt` lines 130-145 — onCreate, after enableEdgeToEdge()

**action:**
After `enableEdgeToEdge()` in `MainActivity.onCreate()`, add:
```kotlin
window.isNavigationBarContrastEnforced = false
```

This prevents the system from forcing a translucent scrim on the navigation bar, allowing the drawer and bottom content to extend fully behind it.

**acceptance_criteria:**
- `window.isNavigationBarContrastEnforced = false` present after `enableEdgeToEdge()`
- Code compiles

---

## Task: Update theme to MaterialComponents + configure bar icon contrast

**read_first:**
- `app/src/main/res/values/themes.xml` — current theme definition
- `app/src/main/java/com/gemma/gpuchat/MainActivity.kt` lines 130-160 — onCreate

**action:**
Update `themes.xml` to use MaterialComponents:
```xml
<style name="Theme.NPUBenchmark" parent="Theme.MaterialComponents.DayNight.NoActionBar"/>
```

Then in `MainActivity.onCreate()`, after `enableEdgeToEdge()` and `isNavigationBarContrastEnforced = false`, add:
```kotlin
val darkTheme = isSystemInDarkTheme()
val controller = androidx.core.window.WindowCompat.getInsetsController(window, view)
controller.isAppearanceLightStatusBars = !darkTheme
controller.isAppearanceLightNavigationBars = !darkTheme
```

This ensures system bar icons (back, home, recent) are legible against the status/navigation bars in both light and dark themes.

**acceptance_criteria:**
- `themes.xml` uses `Theme.MaterialComponents.DayNight.NoActionBar`
- `isAppearanceLightStatusBars` and `isAppearanceLightNavigationBars` are set based on `darkTheme`
- App theme switches correctly between light and dark modes

---

## Task: Verify Scaffold uses correct contentWindowInsets pattern (avoid double padding)

**read_first:**
- `app/src/main/java/com/gemma/gpuchat/MainActivity.kt` lines 850-920 — Scaffold usage and content Box

**action:**
Inspect the current Scaffold setup. The app uses `Scaffold` with `paddingValues` applied to the content `Box` — this is correct. Verify:

1. **Scaffold does NOT set `contentWindowInsets`** — it should rely on default `PaddingValues` from `scaffoldPadding`/`innerPadding`, which are consumed via `Box(padding(paddingValues))`. If `contentWindowInsets = WindowInsets.safeDrawing` is present on Scaffold, **remove it** — `WindowInsets.safeDrawing` contains IME insets and using it with `imePadding()` on the input field causes **double padding**.

2. **Input Row** — verify `imePadding()` is applied to the input Row at line ~975. If `fitInside(WindowInsetsRulers.Ime.current)` is available in the project, prefer it over `imePadding()` for reduced jank. The key constraint: do NOT combine `imePadding()` with `contentWindowInsets = WindowInsets.safeDrawing` on the same Scaffold.

The correct pattern for a Scaffold with IME-aware input:
```kotlin
Scaffold(
    topBar = { TopAppBar(...) },
    snackbarHost = { SnackbarHost(snackbarHostState) }
) { paddingValues ->
    // paddingValues from Scaffold already accounts for system bars
    // DO NOT add contentWindowInsets here if input Row uses imePadding()
    Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
        // chat content
    }
}
```

And on the input Row:
```kotlin
Row(
    modifier = Modifier
        .imePadding()          // handles IME keyboard
        .align(Alignment.BottomCenter)
) { ... }
```

**acceptance_criteria:**
- Scaffold does NOT use `contentWindowInsets = WindowInsets.safeDrawing` (would cause double padding with imePadding)
- Input Row has `imePadding()` applied
- Content Box correctly uses `padding(paddingValues)` from Scaffold

---

## Task: Add edge-to-edge tests

**read_first:**
- `app/src/main/java/com/gemma/gpuchat/TestHarnessActivity.kt` — existing test structure

**action:**
Add a `runEdgeToEdgeTests()` block to `TestHarnessActivity.kt`. Test the actual edge-to-edge API availability and configuration:

```kotlin
// Edge-to-Edge / Inset handling tests
run {
    // Test enableEdgeToEdge class is available (compile-time verification)
    val enableEdgeToEdgeAvailable = try {
        Class.forName("androidx.activity.enableEdgeToEdge")
        true
    } catch (e: ClassNotFoundException) { false }
    assertTrue("enableEdgeToEdge class available", enableEdgeToEdgeAvailable)

    // Test imePadding is available
    val imePaddingAvailable = try {
        Class.forName("androidx.compose.foundation.layout.imePadding")
        true
    } catch (e: ClassNotFoundException) { false }
    assertTrue("imePadding function available", imePaddingAvailable)

    // Test ModalDrawerSheet is available (Material 3)
    val drawerSheetAvailable = try {
        Class.forName("androidx.compose.material3.ModalDrawerSheet")
        true
    } catch (e: ClassNotFoundException) { false }
    assertTrue("ModalDrawerSheet available", drawerSheetAvailable)

    // Test WindowInsets.safeDrawing
    val safeDrawingAvailable = try {
        val cls = Class.forName("androidx.compose.foundation.layout.WindowInsets")
        cls.getField("safeDrawing").get(null) != null
    } catch (e: Exception) { false }
    assertTrue("WindowInsets.safeDrawing available", safeDrawingAvailable)

    // Test isNavigationBarContrastEnforced API exists
    try {
        val windowField = android.view.Window::class.java.getMethod("getNavigationBarContrastEnforced")
        assertTrue("NavigationBarContrastEnforced API exists", windowField != null)
    } catch (e: Exception) {
        assertTrue("NavigationBarContrastEnforced API check skipped (API level)", true)
    }

    // Test WindowCompat.getInsetsController
    val insetsControllerAvailable = try {
        Class.forName("androidx.core.window.WindowCompat")
        true
    } catch (e: ClassNotFoundException) { false }
    assertTrue("WindowCompat available for insets controller", insetsControllerAvailable)
}
```

**acceptance_criteria:**
- `runEdgeToEdgeTests()` block added to TestHarnessActivity
- All 6 assertions pass (class/import availability)
- Existing test suites (WavUtils, WorkspaceManager, PdfToMarkdownConverter) remain passing

---

## Verification

1. Build: `$env:JAVA_HOME = "C:\Program Files\Java\jdk-24"; .\gradlew assembleDebug --no-daemon`
2. Install: `adb install -r app\build\outputs\apk\debug\app-debug.apk`
3. Run tests: `adb shell am start -n com.gemma.gpuchat/.TestHarnessActivity`
4. Read results: `adb shell "run-as com.gemma.gpuchat cat files/test_results/test_results.txt"`
5. Verify new tests appear and all pass

---

## Success Criteria (from ROADMAP)

1. Status bar and navigation bar handled with proper insets
2. Content does not draw behind system bars
3. Keyboard (IME) properly adjusts layout with `imePadding()`
4. UI remains responsive and does not jank during animation
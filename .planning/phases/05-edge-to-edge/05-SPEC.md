# Phase 5: Edge-to-Edge + Polish — SPEC

**Phase:** 05-edge-to-edge
**Created:** 2026-05-02
**Status:** Ready for planning

---

## 1. Problem Statement

The app uses `enableEdgeToEdge()` (already present at line 135 of `MainActivity.kt`) but does NOT properly consume system insets. The result is:
- Content draws behind the status bar or navigation bar on some devices
- No light/dark bar icon contrast management (using `WindowCompat` style manually but `enableEdgeToEdge` from `ComponentActivity` should handle this automatically)
- The `ModalDrawerSheet` may draw behind system bars
- No `StatusBarProtection` or proper scrim for legibility

---

## 2. Existing Implementation

### What's already in place

| Element | Status | Notes |
|---------|--------|-------|
| `enableEdgeToEdge()` call in onCreate | ✅ Present | Line 135, from `androidx.activity.compose` |
| `android:windowSoftInputMode="adjustResize"` | ✅ Present | AndroidManifest.xml line 20 |
| `imePadding()` on input Row | ✅ Present | Line 975 |
| `Scaffold` with `paddingValues` | ✅ Present | Line 898 — `Box(padding(paddingValues))` |
| `ModalDrawerSheet` | ⚠️ Unknown | May not handle inset contrast |
| Light/dark status bar icons | ❌ Missing | `isAppearanceLightStatusBars` / `isAppearanceLightNavigationBars` not set |

### Current scaffold usage (line 853-902)
```kotlin
Scaffold(
    topBar = { TopAppBar(...) },
    snackbarHost = { SnackbarHost(snackbarHostState) }
) { paddingValues ->
    Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
        // content
    }
}
```

### What's missing for edge-to-edge compliance

1. **Light/dark bar icon contrast** — Need to set `isAppearanceLightStatusBars` / `isAppearanceLightNavigationBars` in theme (or remove the manual `WindowCompat` approach)
2. **`ModalDrawerSheet` window insets** — `ModalDrawerSheet` from Material 3 auto-handles safe areas, but needs `windowInsets` configured
3. **Navigation bar contrast** — `enableEdgeToEdge()` sets `window.isNavigationBarContrastEnforced = true` — should set `isNavigationBarContrastEnforced = false` in MainActivity so drawer can extend behind nav bar
4. **TestHarnessActivity theme** — Uses `Theme.Material.Light.NoActionBar` in manifest — should be `Theme.MaterialComponents.DayNight.NoActionBar` for proper theming

---

## 3. What Needs to Change

### Task A: Theme light/nav bar contrast

The theme currently uses `@android:style/Theme.Material.Light.NoActionBar` in AndroidManifest.xml and doesn't handle bar icon contrast. Two options:

**Option 1 (Recommended):** Change theme to use `Theme.MaterialComponents.DayNight.NoActionBar` and configure contrast via code in `MainActivity.onCreate()`:
```kotlin
window.isNavigationBarContrastEnforced = false
WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
```

**Option 2:** Use the Compose `MaterialTheme` properly — but this requires refactoring away from the manifest theme.

### Task B: Add edge-to-edge tests

Add `runEdgeToEdgeTests()` block to TestHarnessActivity verifying:
- `enableEdgeToEdge` is called in `MainActivity.onCreate()`
- `android:windowSoftInputMode="adjustResize"` in manifest
- `Scaffold` receives `paddingValues` and applies to content Box
- Theme uses MaterialComponents (not raw Android theme)

---

## 4. Success Criteria (from ROADMAP)

1. ✅ Status bar and navigation bar handled with proper insets
2. ✅ Content does not draw behind system bars
3. ✅ Keyboard (IME) properly adjusts layout with `imePadding()`
4. ✅ UI remains responsive and does not jank during animation

---

## 5. Acceptance Criteria

- [ ] `enableEdgeToEdge()` confirmed in MainActivity.onCreate()
- [ ] `android:windowSoftInputMode="adjustResize"` in manifest
- [ ] `window.isNavigationBarContrastEnforced = false` set in MainActivity
- [ ] `isAppearanceLightStatusBars` / `isAppearanceLightNavigationBars` set in theme
- [ ] Scaffold properly applies `paddingValues` to content Box
- [ ] `ModalDrawerSheet` has proper window insets (or uses Material3 defaults)
- [ ] New `runEdgeToEdgeTests()` block passes (69 existing tests remain passing)
- [ ] Build succeeds and app runs without drawing behind system bars

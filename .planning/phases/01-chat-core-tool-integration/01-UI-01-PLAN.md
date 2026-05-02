---
phase: 01
plan: UI-01
wave: 1
depends_on: []
requirements_addressed: [UIUX-01, UIUX-03]
autonomous: false
---

# Plan: Drawer Navigation + Build Verification

## Objective
Ensure drawer navigation has all required sections (Chat, Workspace, Settings) and app builds/runs successfully.

## Files Modified
- `app/src/main/java/com/gemma/gpuchat/MainActivity.kt`
- `app/src/main/AndroidManifest.xml` (verify permissions)

## must_haves
- Drawer has Chat, Workspace, Settings navigation items
- App builds via Gradle without errors
- App runs on Nubia device without crash

---

## Task 1: Review Drawer Implementation

<read_first>
- `app/src/main/java/com/gemma/gpuchat/MainActivity.kt` (drawer section, lines 200-400)
</read_first>

<acceptance_criteria>
- ModalDrawerSheet contains NavigationDrawerItem list
- Items for: Chat, Workspace, Settings present
- Each item has label, icon, selected state, onClick
</acceptance_criteria>

<action>
Read MainActivity drawer implementation. Verify all required items exist.
If missing, add NavigationDrawerItem entries for:
- Chat (icon: Icons.Default.Chat or Icons.Default.Home)
- Workspace (icon: Icons.Default.Folder)
- Settings (icon: Icons.Default.Settings)
</action>

---

## Task 2: Verify Permissions in AndroidManifest

<read_first>
- `app/src/main/AndroidManifest.xml`
</read_first>

<acceptance_criteria>
- INTERNET permission present
- RECORD_AUDIO permission present (for Phase 2)
- No redundant or conflicting permissions
</acceptance_criteria>

<action>
Review AndroidManifest.xml. Verify:
1. INTERNET permission exists
2. RECORD_AUDIO permission exists
3. No missing permissions for tools (none needed for intents)
4. android:allowBackup="true"
5. Theme is Material.Light.NoActionBar
</action>

---

## Task 3: Build Debug APK

<read_first>
- `app/build.gradle.kts`
</read_first>

<acceptance_criteria>
- `./gradlew assembleDebug` exits code 0
- APK generated at `app/build/outputs/apk/debug/app-debug.apk`
- No compile errors
</acceptance_criteria>

<action>
Run build:
```powershell
.\gradlew.bat assembleDebug
```

If build fails, fix compilation errors. Common issues:
- Missing imports
- Deprecated API usage (Divider → HorizontalDivider)
- Incorrect function signatures

Delete `app/build` folder if OneDrive sync causes `IOException: not a regular file` errors.
</action>

---

## Task 4: Deploy and Verify on Device

<read_first>
- ADB_QUICKREF.md (if exists)
</read_first>

<acceptance_criteria>
- `adb install -r app/build/outputs/apk/debug/app-debug.apk` succeeds
- App launches without crash
- Drawer opens and navigation works
</acceptance_criteria>

<action>
1. Connect device (Nubia at 192.168.0.17 or verify current IP)
2. Run `adb install -r app/build/outputs/apk/debug/app-debug.apk`
3. Launch app via `adb shell am start -n com.gemma.gpuchat/.MainActivity`
4. Check for crash: `adb logcat -v time --pid=$(adb shell pidof com.gemma.gpuchat)`
5. Verify drawer opens with all items
6. Tap each navigation item to verify no crash

If logcat is silent (Nubia Android 15 issue), use:
`adb logcat -v time --pid=<PID> | Select-String "gemma\|crash\|exception"`
</action>

---

## Verification
1. Build exits code 0
2. APK exists at expected path
3. App installs without error
4. App launches on device
5. Drawer shows all 3 navigation items
6. No crash in logcat within first 10 seconds of launch
---
slug: android-crash-debugging
status: active
trigger: "document how to debug crashes on Android when logcat is silent — use dropbox, dumpsys, and other OEM-specific techniques"
created: 2026-05-03
updated: 2026-05-03
---

# Android Crash Debugging Reference

**Problem:** On some OEM devices (e.g., Nubia Flip 5G Android 15), crashes do NOT appear in `adb logcat` — the system silences them. You must use alternative methods.

---

## Primary Method: `dumpsys dropbox`

The Android DropBoxManager service records system crashes independently of logcat.

```bash
# List all crash reports (newest first, last 2 hours)
adb shell dumpsys dropbox --print -f

# Filter by specific app (package name)
adb shell dumpsys dropbox --print -f 2>/dev/null | findstr "com.gemma.gpuchat"

# Get crashes from today only
adb shell dumpsys dropbox --print -f 2>/dev/null | findstr "crash"

# Get anr (Application Not Responding) reports
adb shell dumpsys dropbox --print -f 2>/dev/null | findstr "anr"
```

**When to use:** App crashes immediately on launch, no logcat output, ClassNotFoundException, ANR.

**Note:** On Nubia Flip 5G, OEM logging may be disabled — crashes only appear in DropBox.

---

## Secondary: `getprop` + `logcat` with process filter

```bash
# Check if ADB is connected and what device
adb devices

# Kill existing logcat and start fresh (buffer size matters on OEM)
adb logcat -v time --clear
adb logcat -v time --pid=$(adb shell pidof com.gemma.gpuchat) 2>&1

# If app just crashed, capture remaining log immediately
adb logcat -v time -d 2>&1 | Select-String "FATAL\|EXCEPTION\|NativeCrash\|crash"

# Check for specific crash types
adb shell "logcat -d -v time | grep -i 'fatal exception\|classnotfoundexception\|ma_invalid'"
```

---

## Inspect APKs for DEX Structure

If crash is `ClassNotFoundException`, verify classes are in the primary DEX:

```powershell
# Using .NET ZipFile — no external tools needed on Windows
Add-Type -AssemblyName System.IO.Compression.FileSystem
$apk = "app\build\outputs\apk\debug\app-debug.apk"
$z = [System.IO.Compression.ZipFile]::OpenRead($apk)
$z.Entries | Where-Object { $_.Name -like "*.dex" } | ForEach-Object {
    Write-Host $_.FullName "->" ($_.Length / 1KB) "KB"
}
$z.Dispose()
```

**Tip:** If classes are spread across secondary DEX files (multiDex), the ART runtime on some devices (Nubia) may fail to resolve them at boot. Use `multiDexKeepFile` to guarantee MainActivity stays in the primary DEX.

---

## Reinstall Clean

Always uninstall before reinstalling when app is crash-looping:

```bash
adb uninstall com.gemma.gpuchat
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

**Why:** A corrupt install can cause ClassNotFoundException even if bytecode is correct.

---

## Debug ANR (App Not Responding)

```bash
# Get ANR traces
adb shell dumpsys activity anr 2>&1 | Select-String "com.gemma.gpuchat" -Context 5

# Also check the traces file
adb shell "cat /data/anr/traces.txt" 2>&1 | Select-String "com.gemma.gpuchat" -Context 10
```

---

## OEM-Specific Notes

### Nubia Flip 5G (Android 15)
- **Global logcat is silenced** — OEM disables most system logging
- **Crash only appears in DropBox** — always check `dumpsys dropbox` first
- **`ClassNotFoundException: MainActivity`** → caused by JVM toolchain mismatch (JDK 21 generates bytecode incompatible with ART on this device). Fix: `jvmToolchain(17)` + multiDex
- `adb connect 192.168.0.20:35703` may need to be re-run after device sleep

---

## Quick Reference Commands

```bash
# Crash debugging checklist
adb devices  # Verify device connected and port
adb uninstall com.gemma.gpuchat  # Clean slate
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb shell dumpsys dropbox --print -f 2>/dev/null | findstr "gpuchat crash anr"
adb shell "run-as com.gemma.gpuchat cat files/gemma_startup.nlog"  # App-level startup log
```
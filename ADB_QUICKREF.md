# ADB Configuration

**Device IP:** 192.168.0.17
**ADB Port:** 45873

## Connect
```bash
adb connect 192.168.0.17:45873
```

## Install APK
```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

## Push Model
```bash
adb push gemma3-1b-it-q4.litertlm /data/data/com.gemma.gpuchat/files/
```

## Start App
```bash
adb shell am start -n com.gemma.gpuchat/.MainActivity
```

## View Logs
```bash
adb logcat -s Gemma4Android
```

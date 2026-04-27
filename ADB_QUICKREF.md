# ADB Configuration

**Device IP:** 192.168.0.20
**ADB Port:** 40975

## Quick Connect
```bash
adb connect 192.168.0.20:40975
```

## Push Model + Install + Start
```bash
# Modelo ja na pasta: Gemma3-1B-IT.litertlm (584MB) no PC
# NOVO: Modelo tambem ja copiado no dispositivo em /sdcard/gemma3-1b-it-q4.litertlm

# Install APK
adb -s 192.168.0.20:40975 install -r app/build/outputs/apk/debug/app-debug.apk

# Start app
adb -s 192.168.0.20:40975 shell am start -n com.gemma.gpuchat/.MainActivity
```

## View Logs (dispositivo pode ter logcat vazio - usar screenshot)
```bash
adb -s 192.168.0.20:40975 logcat -d 2>&1 | Select-String -Pattern "Gemma|LlmChat|Model|Error"
```

## Screenshot (mais confiavel que logcat neste dispositivo)
```bash
adb -s 192.168.0.20:40975 exec-out screencap -p > screen.png
```

## Stop App
```bash
adb -s 192.168.0.20:40975 shell am force-stop com.gemma.gpuchat
```

## Copiar modelo novamente (se precisar)
```bash
# Ja feito: cp /sdcard/Gemma3-1B-IT.litertlm /sdcard/gemma3-1b-it-q4.litertlm
```

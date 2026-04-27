# ADB Configuration

**Device IP:** 192.168.0.17
**ADB Port:** 42005 (ATUALIZADO - verificar com `adb devices`)

## Connect
```bash
adb connect 192.168.0.17:42005
```

## Install APK
```bash
adb -s 192.168.0.17:42005 install -r app/build/outputs/apk/debug/app-debug.apk
```

## Push Model (.litertlm required)
```bash
# Criar diretório
adb -s 192.168.0.17:42005 shell "run-as com.gemma.gpuchat mkdir -p files"
adb -s 192.168.0.17:42005 shell "run-as com.gemma.gpuchat chmod 777 files"

# Push do modelo (troque pelo nome real)
adb -s 192.168.0.17:42005 push gemma3-1b-it-q4.litertlm /data/data/com.gemma.gpuchat/files/
```

## Start App
```bash
adb -s 192.168.0.17:42005 shell am start -n com.gemma.gpuchat/.MainActivity
```

## Stop App
```bash
adb -s 192.168.0.17:42005 shell am force-stop com.gemma.gpuchat
```

## View Logs
```bash
adb -s 192.168.0.17:42005 logcat -d 2>&1 | Select-String -Pattern "Gemma|LlmChat|Error|Exception|FATAL"
```

## Verify Model Files
```bash
adb -s 192.168.0.17:42005 shell "run-as com.gemma.gpuchat ls -la files/"
```

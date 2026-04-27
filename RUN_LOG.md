# Gemma 4 GPU Chat — Android Build & Debug Log

**Dispositivo:** Nubia NX789J (Android 15, build `user`, `release-keys`)
**IP atual:** `192.168.0.20:39383`
**Repositório:** `github.com/mateusoro/GEMMA4-ANDROID`

---

## Problema Original

O logcat nativo do dispositivo retorna **0 linhas** (`logd` desabilitado na ROM Nubia). Toda estratégia de debugging usa arquivo `.nlog` gravado pelo próprio app em `filesDir`.

O modelo Gemma em `/sdcard/` não funciona — LiteRT-LM na native layer não consegue abrir arquivos de `/sdcard/` mesmo com `media_rw`. Erro: `PERMISSION_DENIED: open() failed: /sdcard/gemma3-1b-it-q4.litertlm: Permission denied`.

---

## Solução: modelo em `/data/local/tmp/` (world-readable)

O diretório `/data/local/tmp/` é world-readable e world-writable. O modelo pode ser enviado via `adb push` sem restrições de permissão.

**Arquivo do modelo:** `Gemma3-1B-IT.litertlm` (584MB) — copiado do PC para `/data/local/tmp/gemma3-1b-it-q4.litertlm` no dispositivo.

---

## Compilação

### 1. Corrigir bug em `LlmChatModelHelper.kt`

O código original tinha:
```kotlin
val actualPath: String  // ← ERRO: 'val' cannot be reassigned
```

Correção:
```kotlin
var actualPath: String = modelPath
```

### 2. Adicionar native libraries ao `AndroidManifest.xml`

Conforme a documentação oficial do LiteRT-LM, o GPU backend requer bibliotecas nativas OpenCL declaradas:

```xml
<application
    android:allowBackup="true"
    android:icon="@android:drawable/sym_def_app_icon"
    android:label="Gemma 4 GPU Chat"
    android:theme="@android:style/Theme.Material.Light.NoActionBar">
    <uses-native-library android:name="libvndksupport.so" android:required="false"/>
    <uses-native-library android:name="libOpenCL.so" android:required="false"/>
    <activity ... />
</application>
```

### 3. Alterar busca do modelo no `MainActivity.kt`

O app busca modelos em três locais (em ordem):
1. `/data/local/tmp/` — **NOVO** (world-readable, enviado via `adb push`)
2. `context.filesDir` — internal storage do app
3. `/sdcard/` — storage compartilhado (não funciona para LiteRT-LM native)

```kotlin
val modelCandidates = listOf(
    "gemma3-1b-it-q4.litertlm",
    "gemma4_generic.litertlm",
    "Gemma3-1B-IT.litertlm",
    "Gemma3-1B-IT_multi-prefill-seq_q4_ekv4096.litertlm",
    "Gemma3-1B-IT-LiteRT.litertlm",
    "gemma3-1b-it-q4_ekv.litertlm"
)
val filesDir = context.filesDir
var modelPath: String? = null

// First check /data/local/tmp (world-readable, pushed via ADB)
for (candidate in modelCandidates) {
    val path = "/data/local/tmp/$candidate"
    AppLogger.d(TAG, "Checking: $path")
    if (File(path).exists()) {
        modelPath = path
        AppLogger.d(TAG, "Found model in /data/local/tmp: $candidate at $path")
        break
    }
}

// Then check app's filesDir
if (modelPath == null) {
    for (candidate in modelCandidates) {
        val path = File(filesDir, candidate).absolutePath
        AppLogger.d(TAG, "Checking: $path")
        if (File(path).exists()) {
            modelPath = path
            AppLogger.d(TAG, "Found model: $candidate at $path")
            break
        }
    }
}

// Finally check sdcard
if (modelPath == null) {
    for (candidate in modelCandidates) {
        val path = "/sdcard/$candidate"
        AppLogger.d(TAG, "Checking sdcard: $path")
        if (File(path).exists()) {
            modelPath = path
            AppLogger.d(TAG, "Found model on sdcard: $candidate at $path")
            break
        }
    }
}
```

### 4. Build

```bash
.\gradlew.bat assembleDebug
```

**Resultado:** BUILD SUCCESSFUL

---

## Instalação e Push do Modelo

### 5. Instalar APK

```bash
adb -s 192.168.0.20:39383 install -r app\build\outputs\apk\debug\app-debug.apk
```

**Resultado:** `Performing Streamed Install / Success`

### 6. Enviar modelo para `/data/local/tmp/`

584MB a 11.4 MB/s (~49 segundos):

```bash
adb -s 192.168.0.20:39383 push "C:\Users\Administrador\OneDrive\Área de Trabalho\Mateus\Desenvolvimento\CODEX DESKTOP\GEMMA4 ANDROID\Gemma3-1B-IT.litertlm" /data/local/tmp/gemma3-1b-it-q4.litertlm
```

**Resultado:**
```
Gemma3-1B-IT.litertlm: 1 file pushed, 0 skipped. 11.4 MB/s (584417280 bytes in 48.997s)
```

---

## Execução e Log

### 7. Limpar log anterior e iniciar app

```bash
adb -s 192.168.0.20:39383 shell "run-as com.gemma.gpuchat rm -f files/gemma_startup.nlog"
adb -s 192.168.0.20:39383 shell "am start -n com.gemma.gpuchat/.MainActivity"
```

### 8. Extrair log (após 15 segundos)

```bash
adb -s 192.168.0.20:39383 shell "run-as com.gemma.gpuchat cat files/gemma_startup.nlog"
```

---

## Log Completo da Execução

```
2026-04-27 20:41:48.611 [INFO ] (AppLogger) === LOGGER INITIALIZED ===
2026-04-27 20:41:48.613 [INFO ] (AppLogger) Log file: /data/user/0/com.gemma.gpuchat/files/gemma_startup.nlog
2026-04-27 20:41:48.613 [INFO ] (AppLogger) filesDir: /data/user/0/com.gemma.gpuchat/files
2026-04-27 20:41:48.613 [INFO ] (AppLogger) Android version: 35
2026-04-27 20:41:48.614 [DEBUG] (GemmaApp) === onCreate() called ===
2026-04-27 20:41:48.614 [DEBUG] (GemmaApp) App package: com.gemma.gpuchat
2026-04-27 20:41:48.614 [DEBUG] (GemmaApp) filesDir: /data/user/0/com.gemma.gpuchat/files
2026-04-27 20:41:48.635 [DEBUG] (GemmaApp) onResume() called
2026-04-27 20:41:48.804 [DEBUG] (GemmaApp) LaunchedEffect started - initializing model
2026-04-27 20:41:48.804 [DEBUG] (GemmaApp) Checking: /data/local/tmp/gemma3-1b-it-q4.litertlm
2026-04-27 20:41:48.804 [DEBUG] (GemmaApp) Found model in /data/local/tmp: gemma3-1b-it-q4.litertlm at /data/local/tmp/gemma3-1b-it-q4.litertlm
2026-04-27 20:41:48.804 [INFO ] (GemmaApp) Loading model from: /data/local/tmp/gemma3-1b-it-q4.litertlm
2026-04-27 20:41:48.804 [DEBUG] (LlmChatModelHelper) >>> initialize() CALLED <<<
2026-04-27 20:41:48.804 [DEBUG] (LlmChatModelHelper) modelPath: /data/local/tmp/gemma3-1b-it-q4.litertlm
2026-04-27 20:41:48.804 [DEBUG] (LlmChatModelHelper) filesDir: /data/user/0/com.gemma.gpuchat/files
2026-04-27 20:41:48.805 [DEBUG] (LlmChatModelHelper) Trying GPU backend...
2026-04-27 20:41:48.805 [DEBUG] (LlmChatModelHelper) Backend: com.google.ai.edge.litertlm.Backend$GPU@6417f07 (maxNumTokens=2048)
2026-04-27 20:41:48.805 [DEBUG] (LlmChatModelHelper) EngineConfig created with path: /data/local/tmp/gemma3-1b-it-q4.litertlm
2026-04-27 20:41:48.805 [DEBUG] (LlmChatModelHelper) Creating Engine instance...
2026-04-27 20:41:48.805 [DEBUG] (LlmChatModelHelper) Engine instance created, calling initialize()...
2026-04-27 20:42:04.630 [DEBUG] (LlmChatModelHelper) engine.initialize() returned successfully
2026-04-27 20:42:04.637 [DEBUG] (LlmChatModelHelper) Creating conversation...
2026-04-27 20:42:04.655 [DEBUG] (LlmChatModelHelper) Conversation created: com.google.ai.edge.litertlm.Conversation@48e532a
2026-04-27 20:42:04.655 [INFO ] (LlmChatModelHelper) >>> INITIALIZATION COMPLETE (backend=com.google.ai.edge.litertlm.Backend$GPU@6417f07) <<<
2026-04-27 20:42:04.655 [INFO ] (LlmChatModelHelper) GPU backend SUCCESS!
2026-04-27 20:42:04.656 [INFO ] (GemmaApp) Model initialized successfully!
```

---

## Resumo do Fluxo

| Etapa | Tempo | Resultado |
|-------|-------|-----------|
| App launched | T+0s | Logger inicializado |
| Modelo encontrado em `/data/local/tmp/` | T+0.2s | gemma3-1b-it-q4.litertlm (584MB) |
| GPU backend tentado | T+0.2s | Backend.GPU() criado |
| `engine.initialize()` | T+0.2s → T+16s | **Sucesso em ~16s** |
| Conversation criada | T+16s | Conversation@48e532a |
| Modelo pronto | T+16s | Toast "Modelo pronto!" |

---

## Estrutura de Arquivos do Projeto

```
GEMMA4 ANDROID/
├── app/
│   ├── src/main/
│   │   ├── AndroidManifest.xml          # + libvndksupport.so, libOpenCL.so
│   │   ├── java/com/gemma/gpuchat/
│   │   │   ├── MainActivity.kt          # + busca /data/local/tmp/
│   │   │   ├── LlmChatModelHelper.kt    # + var actualPath (fix reassign)
│   │   │   └── AppLogger.kt             # logger para .nlog
│   │   └── ...
│   └── build.gradle.kts
├── Gemma3-1B-IT.litertlm                # 584MB - enviado ao dispositivo
├── ADB_QUICKREF.md
└── build.gradle.kts
```

---

## Comandos Rápidos (porta atual: 39383)

```bash
# Ver dispositivos
adb devices

# Instalar APK
adb -s 192.168.0.20:39383 install -r app\build\outputs\apk\debug\app-debug.apk

# Push do modelo (se necessário reenviar)
adb -s 192.168.0.20:39383 push "Gemma3-1B-IT.litertlm" /data/local/tmp/gemma3-1b-it-q4.litertlm

# Limpar log e iniciar app
adb -s 192.168.0.20:39383 shell "run-as com.gemma.gpuchat rm -f files/gemma_startup.nlog"
adb -s 192.168.0.20:39383 shell "am start -n com.gemma.gpuchat/.MainActivity"

# Extrair log
adb -s 192.168.0.20:39383 shell "run-as com.gemma.gpuchat cat files/gemma_startup.nlog"

# Verificar modelo em /data/local/tmp
adb -s 192.168.0.20:39383 shell ls -la /data/local/tmp/gemma3-1b-it-q4.litertlm
```
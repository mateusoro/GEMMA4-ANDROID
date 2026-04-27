# Guía de Estudos — Gemma 4 no Android via LiteRT-LM (GPU)

> **Base:** [Começar a usar o LiteRT-LM no Android](https://ai.google.dev/edge/litert-lm/android?hl=pt-br)  
> **LiteRT-LM:** API Kotlin `com.google.ai.edge.litertlm`  
> **Última actualização:** Abril 2026  
> **Idioma:** Português (Brasil)

---

## 1. Visão Geral

Este documento é um guia prático passo a passo para executar modelos **Gemma 4** no Android usando **GPU** como backend de inferência via **LiteRT-LM** (Google). O foco é a aceleração por GPU, que funciona em qualquer dispositivo Android com GPU compatível (Vulkan/OpenCL), sem necessidade de compilação específica por SoC.

**Por que usar GPU?**

- GPU funciona em **qualquer** dispositivo Android com GPU Vulkan/OpenCL — sem dependência de NPU Qualcomm.
- Para modelos de 1B a 3B de parâmetros quantizados (Q4), a GPU pode atingir velocidades de **15-30 tokens/segundo**.
- Não requer compilação AOT para um SoC específico — basta copiar o modelo `.litertlm` genérico.
- A API `com.google.ai.edge.litertlm` abstrai o hardware, mantendo a mesma interface para GPU/NPU/CPU.

**Fluxo Geral:**

```
1. Verificar ligação ADB ao dispositivo
2. Verificar modelo .litertlm
3. Compilar APK com Gradle
4. Instalar APK no dispositivo
5. Copiar modelo para /data/data/<package>/files/
6. Iniciar app e monitorar logs
```

---

## 2. Pré-requisitos

### 2.1 Hardware

| Componente | Requisito |
|------------|-----------|
| **Dispositivo Android** | Qualquer dispositivo com GPU Vulkan/OpenCL (API 29+) |
| **PC Host** | Windows 10/11, 8GB RAM, 20GB espaço livre |
| **Cabo USB** | USB 3.0 ou superior (para ADB rápido) |
| **Rede Wi-Fi** | Host e dispositivo na mesma subrede |

### 2.2 Software

| Ferramenta | Versão |
|------------|--------|
| **Java JDK** | 24 (obrigatório para Gradle 8.9) |
| **Android Studio** | Ladybug ou mais recente |
| **Gradle** | 8.9 (wrapper incluso no projeto) |
| **Android SDK** | API 35+ |
| **ADB** | Versão compatível com Android 15 |

### 2.3 Accessos e Credenciais

```
IP do dispositivo:     192.168.0.26:40101 (ou 192.168.0.26:34063)
Package da app:        com.gemma.npuchat
Diretório de trabalho: C:\Users\Administrador\OneDrive\Área de Trabalho\Mateus\Desenvolvimento\CODEX DESKTOP\GEMMA4 ANDROID\
```

### 2.4 Verificação Rápida do Ambiente

```powershell
# Verificar Java
java -version
# Esperado: openjdk version "24.x.x" ...

# Verificar Gradle
.\gradlew.bat --version
# Esperado: Gradle 8.9

# Verificar ADB
adb version
# Esperado: Android Debug Bridge version 1.0.x

# Testar conexão com dispositivo
adb connect 192.168.0.26:40101
adb devices
# Esperado: 192.168.0.26:40101    device
```

---

## 3. Configuração do Ambiente

### 3.1 Variáveis de Ambiente (Windows)

Edite o **PATH** do sistema ou crie um script de inicialização:

```powershell
# Variáveis recomendadas (adicione ao System Environment Variables)
JAVA_HOME=C:\Program Files\Java\jdk-24
ANDROID_HOME=C:\Users\Administrador\AppData\Local\Android\Sdk
ANDROID_SDK_ROOT=C:\Users\Administrador\AppData\Local\Android\Sdk
PATH=%PATH%;%ANDROID_HOME%\platform-tools;%ANDROID_HOME%\cmdline-tools\latest\bin
```

**Verificação após definição:**

```powershell
# Num novo terminal PowerShell
Write-Output "JAVA_HOME: $env:JAVA_HOME"
Write-Output "ANDROID_HOME: $env:ANDROID_HOME"
```

### 3.2 Ligação ADB ao Dispositivo

```powershell
# Ligação via TCP/IP (Wi-Fi)
adb connect 192.168.0.26:40101

# Verificar estado
adb devices
# Lista de dispositivos conectados

# Se necessitar desinstalar/reinstalar
adb -s 192.168.0.26:40101 uninstall com.gemma.npuchat
```

---

## 4. Estrutura do Projeto

### 4.1 Estrutura de Diretórios

```
pubenchmark_chat/
├── app/
│   ├── src/main/
│   │   ├── java/com/gemma/npuchat/
│   │   │   ├── MainActivity.kt          # Activity principal com Compose UI
│   │   │   ├── LlmChatModelHelper.kt    # Singleton wrapper Engine + Conversation
│   │   │   ├── GemmaRunner.kt           # Legacy v2 (ProcessBuilder)
│   │   │   ├── Message.kt               # Modelo de dados ChatMessage
│   │   │   └── MessageAdapter.kt        # Legacy RecyclerView adapter
│   │   ├── res/
│   │   ├── AndroidManifest.xml
│   │   └── kotlin/                      # Configuração Kotlin metadata
│   ├── build.gradle.kts
│   └── build/outputs/apk/debug/
│       └── app-debug.apk                # ~311MB
├── gradle/wrapper/
│   └── gradle-8.9.jar
├── gradlew.bat
├── settings.gradle.kts
└── build.gradle.kts
```

### 4.2 Ficheiros Principais

| Ficheiro | Função |
|----------|--------|
| `MainActivity.kt` | UI Jetpack Compose, gestão de UiState, chamadas LiteRT diretas |
| `LlmChatModelHelper.kt` | Wrapper singleton — inicializa Engine, cria Conversation, gere backend (GPU/CPU) |
| `LiteRtChatEngine.kt` | (Projeto de referência) — Engine wrapper completo |
| `MainViewModel.kt` | (Projeto de referência) — ViewModel com estado reactivo |
| `ModelManager.kt` | (Projeto de referência) — Download e verificação de modelo |

### 4.3 Configurações Importantes do Gradle

**`app/build.gradle.kts`** — Bits relevantes:

```kotlin
plugins {
    kotlin("android") version "2.1.0"
    id("com.android.application") version "8.5.0")
}

android {
    compileSdk = 35
    defaultConfig {
        applicationId = "com.gemma.npuchat"
        minSdk = 29
        targetSdk = 35
        ndk {
            abiFilters += "arm64-v8a"
        }
    }
}

dependencies {
    // LiteRT-LM para Android — GPU backend
    implementation("com.google.ai.edge.litertlm:litertlm-android:latest.release")
    implementation("androidx.compose.ui:ui:1.7.0")
    implementation("androidx.compose.material3:material3:1.3.0")
}
```

> **Nota:** O backend GPU **não** requer bibliotecas nativas extras no AndroidManifest. Se futuramente quiser adicionar NPU, será necessário adicionar as libs nativas ao manifest (ver docs oficiais).

---

## 5. Fluxo de Trabalho Passo a Passo

### 5.1 Passo 1 — Verificar Dispositivo

```powershell
# Ligar ao dispositivo
adb connect 192.168.0.26:40101

# Verificar SoC e Android
adb -s 192.168.0.26:40101 shell getprop ro.product.device
adb -s 192.168.0.26:40101 shell getprop ro.build.version.release

# Verificar espaço disponível
adb -s 192.168.0.26:40101 shell df -h /data
```

### 5.2 Passo 2 — Obter Modelo

#### Opção A: Gemma3-1B-IT Q4 E2B (recomendado para GPU)

```powershell
# Download direto
curl -L "https://huggingface.co/litert-community/gemma3-1b-it-q4-litert-lm/resolve/main/gemma3-1b-it-q4.litertlm" `
     -o gemma3-1b-it-q4.litertlm

# ~2.6GB — verificação
dir gemma3-1b-it-q4.litertlm
# Esperado: ~2.600.000.000 bytes
```

#### Opção B: Gemma2 2B (alternativa menor, ~500MB)

```powershell
curl -L "https://huggingface.co/MiCkSoftware/Gemma2-2B-IT-LiteRT/resolve/main/Gemma2-2B-IT-LiteRT.litertlm" `
     -o Gemma2-2B-IT-LiteRT.litertlm
```

### 5.3 Passo 3 — Compilar APK

```powershell
# Configurar JAVA_HOME (obrigatório no Windows)
$env:JAVA_HOME = "C:\Program Files\Java\jdk-24"

# Compilar APK debug
.\gradlew.bat assembleDebug

# Verificar output
dir app\build\outputs\apk\debug\
# Deverá existir: app-debug.apk (~311MB)
```

> **Nota (Windows):** O Gradle no Windows pode não produzir output em stdout/stderr mesmo quando a compilação está a proceder corretamente. Se o comando terminar sem erro, verifique a existência do APK em `app\build\outputs\apk\debug\app-debug.apk`.

### 5.4 Passo 4 — Instalar APK

```powershell
# Instalar (ou reinstalar) APK no dispositivo
adb install -r app\build\outputs\apk\debug\app-debug.apk

# Se a instalação falhar com erro de assinatura, desinstale primeiro:
adb uninstall com.gemma.npuchat
adb install app\build\outputs\apk\debug\app-debug.apk
```

### 5.5 Passo 5 — Copiar Modelo para o Dispositivo

```bash
# Criar diretório de modelo (se não existir)
adb -s 192.168.0.26:40101 shell mkdir -p /data/data/com.gemma.npuchat/files/

# Copiar modelo para o diretório da app
adb -s 192.168.0.26:40101 push gemma3-1b-it-q4.litertlm /data/data/com.gemma.npuchat/files/

# Verificar cópia
adb -s 192.168.0.26:40101 shell ls -lh /data/data/com.gemma.npuchat/files/
```

### 5.6 Passo 6 — Iniciar App

```bash
# Iniciar a aplicação
adb shell am start -n com.gemma.npuchat/.MainActivity

# Se necessitar forçar paragem primeiro:
adb shell am force-stop com.gemma.npuchat
adb shell am start -n com.gemma.npuchat/.MainActivity
```

### 5.7 Passo 7 — Monitorizar Logs

```bash
# Ver todos os logs relevantes
adb logcat -d | findstr "GEMMA" "LlmChatModelHelper" "LiteRT"

# Log em tempo real (Ctrl+C para sair)
adb logcat -d | findstr "GEMMA" "LiteRT" "Engine" "Conversation"

# Log completo (para diagnóstico profundo)
adb logcat -d > VERBOSE_LOG.txt
```

### 5.8 Passo 8 — Capturar Estado da UI

```bash
# Capturar screenshot
adb -s 192.168.0.26:40101 exec-out screencap -p > screenshot_$(date +%Y%m%d_%H%M%S).png

# Listar atividades visíveis
adb shell dumpsys activity activities | findstr "mResumed" "mPaused"
```

---

## 6. API LiteRT — Referência Rápida (GPU)

### 6.1 Seletor de Backend

```kotlin
import com.google.ai.edge.litertlm.Backend

// GPU — recomendado, funciona em qualquer dispositivo com Vulkan/OpenCL
val backend = Backend.GPU()

// CPU — garantia de funcionamento, mais lento
val backend = Backend.CPU()

// NPU — requer dispositivo com Qualcomm NPU e libs nativas
val backend = Backend.NPU(nativeLibraryDir = context.applicationInfo.nativeLibraryDir)
```

### 6.2 Inicialização do Motor

```kotlin
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig

// Caminho do modelo no dispositivo
val modelPath = "${context.filesDir}/gemma3-1b-it-q4.litertlm"

val engineConfig = EngineConfig(
    modelPath = modelPath,
    backend = Backend.GPU(),
    maxNumTokens = 2048   // máximo de tokens em contexto
)

val engine = Engine(engineConfig)
engine.initialize()
```

### 6.3 Criação de Conversa

```kotlin
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SamplerConfig

val samplerConfig = SamplerConfig(topK = 10, topP = 0.95, temperature = 0.8)

val convConfig = ConversationConfig(
    samplerConfig = samplerConfig
)
val conversation = engine.createConversation(convConfig)
```

### 6.4 Envio de Mensagem (Streaming com Callback)

```kotlin
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback

val callback = object : MessageCallback {
    override fun onMessage(message: Message) {
        // Token recebido — atualizar UI
        val token = message.text
        appendToResponse(token)
    }

    override fun onDone() {
        // Resposta completa — marca fim
        finishResponse()
    }

    override fun onError(throwable: Throwable) {
        // Erro — mostrar mensagem
        showError(throwable.message)
    }
}

// Envio assíncrono
conversation.sendMessageAsync(
    Contents.of(listOf(Content.Text(input))),
    callback
)
```

### 6.5 Ciclo de Vida Completo

```kotlin
class GemmaChat(private val context: Context) {

    private var engine: Engine? = null
    private var conversation: Conversation? = null

    fun initialize(modelName: String) {
        val backend = Backend.GPU()
        val modelPath = "${context.filesDir}/$modelName"

        val engineConfig = EngineConfig(
            modelPath = modelPath,
            backend = backend,
            maxNumTokens = 2048
        )
        engine = Engine(engineConfig).apply { initialize() }

        val samplerConfig = SamplerConfig(topK = 10, topP = 0.95, temperature = 0.8)
        conversation = engine!!.createConversation(
            ConversationConfig(samplerConfig = samplerConfig)
        )
    }

    fun sendMessage(input: String, callback: MessageCallback) {
        conversation?.sendMessageAsync(
            Contents.of(listOf(Content.Text(input))),
            callback
        )
    }

    fun shutdown() {
        conversation?.close()
        engine?.close()
    }
}
```

### 6.6 Estruturas de Dados Principais

| Estrutura | Função |
|-----------|--------|
| `EngineConfig` | Configuração do motor — caminho do modelo, backend, max tokens |
| `Backend.GPU/CPU/NPU` | Seletor de hardware de execução |
| `ConversationConfig` | Configuração da conversa — samplerConfig, systemInstruction |
| `MessageCallback` | Callbacks assíncronos — `onMessage`, `onDone`, `onError` |
| `Contents.of()` | Helper para criar conteúdos a enviar ao modelo |
| `Content.Text()` | Conteúdo textual individual |

---

## 7. Multimodalidade (Opcional)

Se o modelo suporte imagem/áudio:

```kotlin
val engineConfig = EngineConfig(
    modelPath = "/path/to/model.litertlm",
    backend = Backend.GPU(),
    visionBackend = Backend.GPU(),  // opcional
    audioBackend = Backend.CPU()    // opcional
)

conversation.sendMessage(Contents.of(
    Content.ImageFile("/path/to/image"),
    Content.Text("Descreva esta imagem.")
))
```

---

## 8. Erros Comuns e Soluções

### 8.1 Tabela de Erros

| Código / Erro | Causa Provável | Solução |
|---------------|----------------|---------|
| `FileNotFoundException` | Modelo não encontrado no caminho especificado | Verificar caminho em `adb shell ls /data/data/com.gemma.npuchat/files/` |
| `IllegalStateException: engine not initialized` | Engine não foi inicializado antes de criar Conversation | Chamar `engine.initialize()` antes de `createConversation()` |
| App crasha silenciosamente | Falha no carregamento de biblioteca nativa | Consultar tombstone: `adb shell ls /data/tombstones/` |
| `SendMessageAsync called after close` | Conversa fechada antes de nova chamada | Recriar Conversation após shutdown |
| Build falha com `Symbol missing` | Conflito de versão LiteRT / Kotlin metadata | Adicionar `-Xskip-metadata-version-check` em compilerOptions |

### 8.2 Diagnóstico por Log

```bash
# Erro de backend
adb logcat -d | findstr "Backend" "FAILED"

# Erro de inicialização
adb logcat -d | findstr "initialize" "Engine" "Exception"

# Erro de modelo
adb logcat -d | findstr "NOT_FOUND" "FileNotFound" "tensor"

# Erro nativo (JNI)
adb logcat -d | findstr "JNI" "UnsatisfiedLink" "dlopen"
```

### 8.3 Fallback Strategy

```kotlin
fun createBackend(): Backend {
    return try {
        Backend.GPU()
    } catch (e: Throwable) {
        try {
            Backend.CPU()
        } catch (e: Throwable) {
            throw IllegalStateException("Nenhum backend disponível")
        }
    }
}
```

---

## 9. Recursos

### 9.1 Documentação Oficial

| Recurso | URL |
|---------|-----|
| LiteRT-LM Android (base) | https://ai.google.dev/edge/litert-lm/android?hl=pt-br |
| LiteRT-LM GitHub | https://github.com/google-ai-edge/LiteRT |
| LiteRT-LM API (Kotlin) | https://ai.google.dev/docs/litert/kotlins |
| Gemma on LiteRT | https://ai.google.dev/docs/litert/gemma |

### 9.2 Modelos Disponíveis

| Modelo | URL de Download | Notas |
|--------|-----------------|-------|
| Gemma3-1B-IT Q4 E2B | [huggingface.co/litert-community/gemma3-1b-it-q4-litert-lm](https://huggingface.co/litert-community/gemma3-1b-it-q4-litert-lm/resolve/main/gemma3-1b-it-q4.litertlm) | ~2.6GB, genérico GPU/NPU |
| Gemma2-2B-IT LiteRT | [huggingface.co/MiCkSoftware/Gemma2-2B-IT-LiteRT](https://huggingface.co/MiCkSoftware/Gemma2-2B-IT-LiteRT/resolve/main/Gemma2-2B-IT-LiteRT.litertlm) | ~500MB, alternativa menor |

### 9.3 Projeto de Referência

O diretório `litertlm-gemma4-sm8750-chat/` na workspace contém uma implementação de referência completa com:

- `LiteRtChatEngine.kt` — Wrapper robusto do engine LiteRT
- `MainViewModel.kt` — ViewModel com gestão de estado e erros
- `ModelManager.kt` — Download e verificação de modelo
- `README.md` — Documentação do projeto de referência

---

*Este guia foi compilado com base na documentação oficial do LiteRT-LM (https://ai.google.dev/edge/litert-lm/android) em Abril de 2026.*

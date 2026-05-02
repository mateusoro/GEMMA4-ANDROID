@file:OptIn(ExperimentalMaterial3Api::class)

package com.gemma.gpuchat

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.gemma.gpuchat.WorkspaceManager
import com.gemma.gpuchat.AgentTools
import com.google.ai.edge.litertlm.Channel
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.tool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "GemmaApp"

// System instruction for the agent — tells Gemma about its available tools
// <|think|> at the very start enables Gemma's built-in thinking/reasoning mode
private fun getAgentSystemInstruction(): Contents {
    val prompt = """You can do function call.
You have access to these functions:
- listWorkspace() -> lists all files in the workspace (documents and markdown)
- listMarkdown() -> lists only .md files in the workspace
- readWorkspaceFile(filename) -> reads a file content. Pass just the filename like "documento.md", the function searches in both markdown/ and documents/ folders automatically
- saveMarkdownFile(filename, content) -> saves a .md file
- showLocationOnMap(location) -> opens the map with the specified location
- createCalendarEvent(datetime, title) -> creates a calendar event. datetime format: "2026-05-15T14:00:00"
- getDeviceInfo() -> returns current date, time and memory info

When a user asks you to read, list or manage files, always call listWorkspace() first to see what files exist, then use readWorkspaceFile() to read content.

Current date: ${java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"))}
    """.trimIndent()

    return Contents.of(prompt)
}

// Channel config for Gemma's built-in thinking/reasoning output
// The model outputs reasoning between <|channel>thought\n and <channel|> markers
// This content goes into Message.channels["thought"]
private fun getThinkingChannel(): Channel {
    return Channel(
        channelName = "thought",
        start = "<|channel>thought\n",
        end = "<channel|>"
    )
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            AppLogger.init(this)
            AppLogger.d(TAG, "=== onCreate() called ===")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Logger init failed", e)
        }
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                ChatScreen()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        LlmChatModelHelper.release()
    }
}

@Composable
fun ChatScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()

    var messages by remember { mutableStateOf(listOf<ChatMessage>()) }
    var inputText by remember { mutableStateOf("") }
    var isModelReady by remember { mutableStateOf(false) }
    var audioTranscriber by remember { mutableStateOf<AudioTranscriber?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showLogs by remember { mutableStateOf(false) }
    var logContent by remember { mutableStateOf("") }

    // Loading UI state
    var initStage by remember { mutableStateOf("Iniciando...") }
    var initProgress by remember { mutableStateOf(0f) }
    var isInitializing by remember { mutableStateOf(true) }
    var memoryInfo by remember { mutableStateOf<LlmChatModelHelper.MemoryInfo?>(null) }
    var systemMemoryInfo by remember { mutableStateOf<LlmChatModelHelper.SystemMemoryInfo?>(null) }
    var throughput by remember { mutableStateOf(0f) }

    // Auto message state: 0=none, 1=oi sent, 2=done
    var autoMessageState by remember { mutableStateOf(0) }
    var lastResponseTokenCount by remember { mutableStateOf(0) }
    var lastResponseDurationMs by remember { mutableStateOf(0L) }
    var responseStartTime by remember { mutableStateOf(0L) }

    // Settings dialog state
    var showSettings by remember { mutableStateOf(false) }
    var settings by remember { mutableStateOf(LlmPreferences.Settings()) }
    var isReloading by remember { mutableStateOf(false) }

    // Drawer state for ModalNavigationDrawer
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    // Conversation / chat history state
    var conversations by remember { mutableStateOf(listOf<Conversation>()) }
    var currentConversationId by remember { mutableStateOf<String?>(null) }

    // Audio recording state (raw PCM 16kHz mono 16-bit for Gemma 4)
    val AUDIO_SAMPLE_RATE = 16000
    val AUDIO_CHANNEL = AudioFormat.CHANNEL_IN_MONO
    val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    var audioRecord: AudioRecord? by remember { mutableStateOf(null) }
    var recordingJob: kotlinx.coroutines.Job? by remember { mutableStateOf(null) }
    var isRecording by remember { mutableStateOf(false) }
    val audioBuffer = mutableListOf<Byte>()

    fun startRecordingAudio() {
        try {
            val bufferSize = AudioRecord.getMinBufferSize(AUDIO_SAMPLE_RATE, AUDIO_CHANNEL, AUDIO_FORMAT)
            if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
                AppLogger.e(TAG, "Invalid buffer size: $bufferSize")
                return
            }
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                AUDIO_SAMPLE_RATE,
                AUDIO_CHANNEL,
                AUDIO_FORMAT,
                bufferSize
            )
            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                AppLogger.e(TAG, "AudioRecord not initialized")
                audioRecord?.release()
                audioRecord = null
                return
            }
            audioBuffer.clear()
            audioRecord?.startRecording()
            isRecording = true
            AppLogger.i(TAG, "AudioRecord started at 16kHz mono PCM 16-bit")

            recordingJob = scope.launch {
                val buffer = ByteArray(bufferSize)
                val startTime = System.currentTimeMillis()
                val maxDuration = 28000L // 28s to stay under 30s limit
                while (isRecording && System.currentTimeMillis() - startTime < maxDuration) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (read > 0) {
                        for (i in 0 until read) {
                            audioBuffer.add(buffer[i])
                        }
                    }
                    delay(50)
                }
                // Auto-stop at max duration
                if (isRecording) {
                    recordingJob?.cancel()
                    recordingJob = null
                    audioRecord?.stop()
                    audioRecord?.release()
                    audioRecord = null
                    isRecording = false
                    audioBuffer.clear()
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to start AudioRecord", e)
            isRecording = false
        }
    }

    fun stopRecordingAudio(): ByteArray? {
        return try {
            recordingJob?.cancel()
            recordingJob = null
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
            isRecording = false

            val bytes = audioBuffer.toByteArray()
            AppLogger.i(TAG, "Audio recorded: ${bytes.size} bytes (${bytes.size / 2} samples @ 16kHz = ${bytes.size / 2 / 16000.0}s)")
            audioBuffer.clear()
            if (bytes.isEmpty()) null else bytes
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to stop AudioRecord", e)
            audioRecord?.release()
            audioRecord = null
            isRecording = false
            audioBuffer.clear()
            null
        }
    }

    // Permission launcher - defined AFTER the functions it calls
    fun encodeWav(rawPcmBytes: ByteArray, sampleRate: Int, numChannels: Int, bitsPerSample: Int): ByteArray {
        val byteRate = sampleRate * numChannels * bitsPerSample / 8
        val blockAlign = numChannels * bitsPerSample / 8
        val dataSize = rawPcmBytes.size
        val fileSize = 36 + dataSize

        val wav = ByteArray(44 + dataSize)
        // RIFF header
        wav[0] = 'R'.code.toByte(); wav[1] = 'I'.code.toByte(); wav[2] = 'F'.code.toByte(); wav[3] = 'F'.code.toByte()
        wav[4] = (fileSize and 0xFF).toByte(); wav[5] = ((fileSize shr 8) and 0xFF).toByte()
        wav[6] = ((fileSize shr 16) and 0xFF).toByte(); wav[7] = ((fileSize shr 24) and 0xFF).toByte()
        wav[8] = 'W'.code.toByte(); wav[9] = 'A'.code.toByte(); wav[10] = 'V'.code.toByte(); wav[11] = 'E'.code.toByte()
        // fmt chunk
        wav[12] = 'f'.code.toByte(); wav[13] = 'm'.code.toByte(); wav[14] = 't'.code.toByte(); wav[15] = ' '.code.toByte()
        wav[16] = 16; wav[17] = 0; wav[18] = 0; wav[19] = 0  // chunk size = 16
        wav[20] = 1; wav[21] = 0  // audio format = 1 (PCM)
        wav[22] = numChannels.toByte(); wav[23] = 0
        wav[24] = (sampleRate and 0xFF).toByte(); wav[25] = ((sampleRate shr 8) and 0xFF).toByte()
        wav[26] = ((sampleRate shr 16) and 0xFF).toByte(); wav[27] = ((sampleRate shr 24) and 0xFF).toByte()
        wav[28] = (byteRate and 0xFF).toByte(); wav[29] = ((byteRate shr 8) and 0xFF).toByte()
        wav[30] = ((byteRate shr 16) and 0xFF).toByte(); wav[31] = ((byteRate shr 24) and 0xFF).toByte()
        wav[32] = blockAlign.toByte(); wav[33] = 0
        wav[34] = bitsPerSample.toByte(); wav[35] = 0
        // data chunk
        wav[36] = 'd'.code.toByte(); wav[37] = 'a'.code.toByte(); wav[38] = 't'.code.toByte(); wav[39] = 'a'.code.toByte()
        wav[40] = (dataSize and 0xFF).toByte(); wav[41] = ((dataSize shr 8) and 0xFF).toByte()
        wav[42] = ((dataSize shr 16) and 0xFF).toByte(); wav[43] = ((dataSize shr 24) and 0xFF).toByte()
        // raw PCM data
        for (i in rawPcmBytes.indices) {
            wav[44 + i] = rawPcmBytes[i]
        }
        return wav
    }

    // Permission launcher - defined AFTER the functions it calls
    val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startRecordingAudio()
        }
    }

    // Handler for UI thread updates from background callbacks - must be before pdfPickerLauncher
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    // Initialize WorkspaceManager
    LaunchedEffect(Unit) {
        WorkspaceManager.init(context)
    }

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        AppLogger.i(TAG, "PDF selected: $uri")
        if (uri != null) {
            // Take persistent read permission so we can access later
            val takeFlags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            try {
                context.contentResolver.takePersistableUriPermission(uri, takeFlags)
                AppLogger.d(TAG, "Persistable URI permission granted for: $uri")
            } catch (e: Exception) {
                AppLogger.w(TAG, "Could not take persistable permission: ${e.message}")
            }

            // Add loading message
            val loadingMsg = ChatMessage(text = "📄 Processando PDF...", isUser = false)
            messages = messages + loadingMsg

            // Convert PDF to markdown in IO thread
            scope.launch {
                try {
                    val markdown = withContext(Dispatchers.IO) {
                        val converter = PdfToMarkdownConverter(context)
                        try {
                            val inputStream = context.contentResolver.openInputStream(uri)
                            if (inputStream != null) {
                                AppLogger.d(TAG, "InputStream opened successfully for: $uri")
                                val result = converter.convert(inputStream)
                                inputStream.close()
                                AppLogger.i(TAG, "PDF converted to Markdown (${result.length} chars)")
                                result
                            } else {
                                AppLogger.e(TAG, "InputStream was null for URI: $uri")
                                "Erro: não foi possível abrir o PDF (input stream null)"
                            }
                        } catch (e: Exception) {
                            AppLogger.e(TAG, "PDF conversion exception: ${e.message}", e)
                            "Erro ao processar PDF: ${e.message}"
                        }
                    }
                    // Remove loading message
                    messages = messages.filter { !it.text.startsWith("📄 Processando") }
                    // Salvar PDF e Markdown no workspace
                    val pdfPath = WorkspaceManager.savePdf(context, uri)
                    val mdFileName = uri.lastPathSegment?.substringAfterLast('/')?.replace(".pdf", "") ?: "document"
                    val mdPath = WorkspaceManager.saveMarkdown(context, mdFileName, markdown)
                    val workspaceInfo = buildString {
                        appendLine("📁 Workspace atualizado:")
                        if (pdfPath != null) appendLine("   ✓ PDF salvo: workspace/documents/${File(pdfPath).name}")
                        if (mdPath != null) appendLine("   ✓ Markdown salvo: workspace/markdown/${File(mdPath).name}")
                    }
                    // Add PDF content as user message
                    val pdfMsg = ChatMessage(text = markdown, isUser = true)
                    messages = messages + pdfMsg
                    // Add bot response placeholder
                    messages = messages + ChatMessage(text = "", isUser = false)
                    val startTime = System.currentTimeMillis()
                    AppLogger.i(TAG, "PDF converted to Markdown (${markdown.length} chars), sending to model")
                    val labeledMarkdown = "$workspaceInfo\n\nPDF ENVIADO PELO USUÁRIO:\n$markdown"
                    LlmChatModelHelper.sendMessage(
                        message = labeledMarkdown,
                        onToken = { token ->
                            AppLogger.d(TAG, "[PDF-RESP-TOKEN] $token")
                            mainHandler.post {
                                val lastBot = messages.indexOfLast { !it.isUser }
                                if (lastBot >= 0) {
                                    messages = messages.mapIndexed { idx, msg ->
                                        if (idx == lastBot) msg.copy(text = msg.text + token) else msg
                                    }
                                }
                            }
                        },
                        onDone = {
                            AppLogger.i(TAG, "[PDF-RESP-DONE]")
                            mainHandler.post {
                                val lastBot = messages.indexOfLast { !it.isUser }
                                if (lastBot >= 0) {
                                    val text = messages[lastBot].text
                                    val duration = System.currentTimeMillis() - startTime
                                    val tp = if (duration > 0) text.length * 1000f / duration else 0f
                                    throughput = tp
                                    messages = messages.mapIndexed { idx, msg ->
                                        if (idx == lastBot) msg.copy(throughput = tp, tokenCount = text.length, durationMs = duration) else msg
                                    }
                                }
                                memoryInfo = LlmChatModelHelper.getMemoryUsage()
                                systemMemoryInfo = LlmChatModelHelper.getSystemMemory(context)
                                currentConversationId?.let { convId ->
                                    scope.launch {
                                        val conv = Conversation(
                                            id = convId,
                                            title = messages.firstOrNull { it.isUser }?.text?.take(30) ?: "PDF",
                                            messages = messages.toList(),
                                            createdAt = System.currentTimeMillis(),
                                            updatedAt = System.currentTimeMillis()
                                        )
                                        ChatHistoryManager.saveConversation(context, conv)
                                    }
                                }
                            }
                        },
                        onError = { error ->
                            AppLogger.e(TAG, "PDF model error: ${error.message}", error)
                            mainHandler.post {
                                messages = messages.filter { !it.text.startsWith("📄 Processando") }
                                val lastBot = messages.indexOfLast { !it.isUser }
                                if (lastBot >= 0) {
                                    messages = messages.mapIndexed { idx, msg ->
                                        if (idx == lastBot) msg.copy(text = "Erro PDF: ${error.message}") else msg
                                    }
                                }
                            }
                        }
                    )
                } catch (e: Exception) {
                    AppLogger.e(TAG, "PDF conversion error: ${e.message}", e)
                    messages = messages.filter { !it.text.startsWith("📄 Processando") }
                    messages = messages + ChatMessage(text = "Erro ao processar PDF: ${e.message}", isUser = false)
                }
            }
        }
    }

    // Load saved settings
    LaunchedEffect(Unit) {
        settings = LlmPreferences.getSettingsFlow(context).first()
    }

    // Load conversations from ChatHistoryManager
    LaunchedEffect(Unit) {
        ChatHistoryManager.getConversationsFlow(context).collect { convList ->
            conversations = convList
        }
    }

    // Initialize model
    LaunchedEffect(Unit) {
        AppLogger.d(TAG, "LaunchedEffect started - initializing model")
        isInitializing = true
        initStage = "Procurando modelo..."
        initProgress = 0f

        try {
            val modelCandidates = listOf(
                "gemma-4-E2B-it.litertlm",
                "gemma3-1b-it-q4.litertlm",
                "gemma4_generic.litertlm",
                "Gemma3-1B-IT.litertlm",
                "Gemma3-1B-IT_multi-prefill-seq_q4_ekv4096.litertlm",
                "Gemma3-1B-IT-LiteRT.litertlm",
                "gemma3-1b-it-q4_ekv.litertlm"
            )
            val filesDir = context.filesDir
            var modelPath: String? = null

            // First check /data/local/tmp
            for (candidate in modelCandidates) {
                val path = "/data/local/tmp/$candidate"
                AppLogger.d(TAG, "Checking: $path")
                if (File(path).exists()) {
                    modelPath = path
                    AppLogger.d(TAG, "Found model in /data/local/tmp: $candidate at $path")
                    initStage = "Modelo encontrado: $candidate"
                    initProgress = 0.05f
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
                        initStage = "Modelo encontrado: $candidate"
                        initProgress = 0.05f
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
                        initStage = "Modelo encontrado: $candidate"
                        initProgress = 0.05f
                        break
                    }
                }
            }

            if (modelPath == null) {
                val tried = modelCandidates.joinToString()
                AppLogger.e(TAG, "No model found! Tried: $tried")
                throw Exception("No model found! Tried: $tried")
            }

            AppLogger.i(TAG, "Loading model from: $modelPath")
            initStage = "Carregando modelo..."
            initProgress = 0.1f

            // Create AgentTools (once, outside IO thread)
            val agentTools = listOf(tool(AgentTools.create(context)))
            val sysInstruction = getAgentSystemInstruction()
            val thinkingChannel = getThinkingChannel()
            AppLogger.d(TAG, "AgentTools created: ${agentTools.size} ToolProviders, thinking enabled")

            // Run initialization on IO thread with UI-safe callbacks
            val params = LlmPreferences.settingsToLlmParams(settings)
            withContext(Dispatchers.IO) {
                LlmChatModelHelper.initialize(
                    context, modelPath, params, agentTools, sysInstruction,
                    listOf(thinkingChannel),
                    mapOf("enable_thinking" to true)
                ) { stage, progress ->
                    // Post to main thread for Compose recomposition
                    mainHandler.post {
                        initStage = stage
                        initProgress = progress / 100f
                        AppLogger.d(TAG, "[PROGRESS] $stage ($progress%)")
                    }
                }
            }

            isModelReady = true
            isInitializing = false
            // Initialize AudioTranscriber with its own engine/conversation (isolated from chat)
            withContext(Dispatchers.IO) {
                audioTranscriber = AudioTranscriber(context)
                audioTranscriber!!.initialize { stage, progress ->
                    mainHandler.post {
                        initStage = "$stage (${progress}%)"
                    }
                }
            }
            memoryInfo = LlmChatModelHelper.getMemoryUsage()
            systemMemoryInfo = LlmChatModelHelper.getSystemMemory(context)
            initStage = "Pronto!"
            initProgress = 1f
            AppLogger.i(TAG, "Model initialized successfully!")

            // Auto-test: send "oi" to trigger thinking mode
            autoMessageState = 1
        } catch (e: Exception) {
            isInitializing = false
            AppLogger.e(TAG, "Model init failed", e)
            initStage = "Erro: ${e.message}"
            errorMessage = "Model init failed: ${e.message}"
            snackbarHostState.showSnackbar("Model init failed: ${e.message}")
        }
    }

    // Helper to find last bot message index safely
    fun getLastBotMessageIndex(msgs: List<ChatMessage>): Int {
        return msgs.indexOfLast { !it.isUser }
    }

    // Auto-test: send "oi" when model is ready to test thinking mode
    LaunchedEffect(isModelReady, autoMessageState) {
        if (isModelReady && autoMessageState == 1) {
            AppLogger.i(TAG, "[AUTO] Sending 'oi' to test thinking mode...")
            val userMsg = ChatMessage(text = "oi", isUser = true)
            messages = messages + userMsg
            val botMsg = ChatMessage(text = "", isUser = false)
            messages = messages + botMsg
            val startTime = System.currentTimeMillis()

            LlmChatModelHelper.sendMessage(
                message = "oi",
                onToken = { token ->
                    AppLogger.d(TAG, "[THINK-TOKEN] $token")
                    messages = messages.mapIndexed { index, msg ->
                        if (index == getLastBotMessageIndex(messages) && !msg.isUser) {
                            msg.copy(text = msg.text + token)
                        } else msg
                    }
                },
                onDone = {
                    val lastBotIdx = messages.indexOfLast { !it.isUser }
                    val duration = System.currentTimeMillis() - startTime
                    val count = if (lastBotIdx >= 0) messages[lastBotIdx].text.length else 0
                    val tp = if (duration > 0) (count * 1000f) / duration else 0f
                    throughput = tp
                    AppLogger.i(TAG, "[AUTO-DONE] 'oi' response: $count chars in ${duration}ms (${tp} tk/s)")
                    AppLogger.i(TAG, "=== THINKING MODE TEST COMPLETE ===")
                    autoMessageState = 2
                },
                onError = { error ->
                    AppLogger.e(TAG, "[AUTO-ERROR] ${error.message}", error)
                    autoMessageState = 2
                }
            )
        }
    }

    // Helper to send a message and chain to next state
    fun sendAutoMessage(text: String, nextState: Int, onDone: () -> Unit) {
        val userMsg = ChatMessage(text = text, isUser = true)
        messages = messages + userMsg
        val botMsg = ChatMessage(text = "", isUser = false)
        messages = messages + botMsg
        val prefix = "AUTO$nextState"
        val startTime = System.currentTimeMillis()
        LlmChatModelHelper.sendMessage(
            message = text,
            onToken = { token ->
                AppLogger.d(TAG, "[$prefix-TOKEN] $token")
                messages = messages.mapIndexed { index, msg ->
                    if (index == getLastBotMessageIndex(messages) && !msg.isUser) {
                        msg.copy(text = msg.text + token)
                    } else msg
                }
            },
            onDone = {
                AppLogger.i(TAG, "[$prefix-DONE] Response complete")
                val lastBotIdx = messages.indexOfLast { !it.isUser }
                if (lastBotIdx >= 0) {
                    val tokenCount = messages[lastBotIdx].text.length
                    val duration = System.currentTimeMillis() - startTime
                    val tp = if (duration > 0) (tokenCount * 1000f) / duration else 0f
                    throughput = tp
                    AppLogger.i(TAG, "[$prefix-THROUGHPUT] tk/s=$tp count=$tokenCount dur=$duration")
                    messages = messages.mapIndexed { idx, msg ->
                        if (idx == lastBotIdx) {
                            msg.copy(throughput = tp, tokenCount = tokenCount, durationMs = duration)
                        } else msg
                    }
                }
                autoMessageState = nextState
                onDone()
            },
            onError = { error ->
                AppLogger.e(TAG, "[$prefix-ERROR] ${error.message}", error)
            }
        )
    }

    // Cleanup on dispose
    DisposableEffect(Unit) {
        onDispose {
            AppLogger.d(TAG, "Disposing - releasing model")
            LlmChatModelHelper.release()
        }
    }

    // Auto-scroll on new messages
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(modifier = Modifier.width(300.dp)) {
                // Drawer header
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(24.dp)
                ) {
                    Column {
                        Text(
                            text = "Conversas",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${conversations.size} conversa(s)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                }

                // New conversation button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            scope.launch {
                                val newId = ChatHistoryManager.createNewConversation(context, "Nova conversa")
                                currentConversationId = newId
                                messages = emptyList()
                                drawerState.close()
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "Nova conversa",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Nova conversa",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                HorizontalDivider()

                // Conversation list
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(conversations, key = { it.id }) { conv ->
                        val isSelected = conv.id == currentConversationId
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    scope.launch {
                                        currentConversationId = conv.id
                                        messages = conv.messages
                                        drawerState.close()
                                    }
                                }
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.secondaryContainer
                                    else MaterialTheme.colorScheme.surface
                                )
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = conv.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer
                                    else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${conv.messages.size} msgs",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        ChatHistoryManager.deleteConversation(context, conv.id)
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Delete,
                                    contentDescription = "Apagar",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }

                HorizontalDivider()

                // Settings button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            scope.launch { drawerState.close() }
                            showSettings = true
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = "Settings",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // Show Logs button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            scope.launch { drawerState.close() }
                            logContent = AppLogger.readLogFile()
                            showLogs = !showLogs
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.List,
                        contentDescription = "Show Logs",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = if (showLogs) "Hide Logs" else "Show Logs",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Chat",
                            style = MaterialTheme.typography.titleMedium
                        )
                        HorizontalDivider(
                            modifier = Modifier
                                .height(20.dp)
                                .width(1.dp),
                            color = MaterialTheme.colorScheme.outline
                        )
                        systemMemoryInfo?.let { sys ->
                            val usedGb = sys.usedMb / 1024.0
                            val totalGb = sys.totalMb / 1024.0
                            Text(
                                text = "RAM: ${String.format("%.1f", usedGb)}/${String.format("%.1f", totalGb)}GB",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        scope.launch {
                            if (drawerState.isClosed) drawerState.open() else drawerState.close()
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Filled.Menu,
                            contentDescription = "Menu",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Loading overlay
            if (isInitializing) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(64.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "Carregando Gemma...",
                        style = MaterialTheme.typography.headlineSmall
                    )
                }
            }

            // Normal chat content
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                if (showLogs) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(8.dp)
                    ) {
                        LazyColumn {
                            items(logContent.lines().takeLast(50)) { line ->
                                Text(
                                    text = line,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                    Text(
                        text = "Log: ${AppLogger.getLogFilePath()}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item { Spacer(modifier = Modifier.height(8.dp)) }
                    items(messages) { message ->
                        ChatBubble(
                            message = message,
                            showMetrics = !message.isUser && message.tokenCount > 0
                        )
                    }
                    item { Spacer(modifier = Modifier.height(8.dp)) }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .imePadding()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Type a message...") },
                        enabled = isModelReady,
                        singleLine = true,
                        shape = RoundedCornerShape(24.dp)
                    )
                    IconButton(
                        onClick = {
                            if (isRecording) {
                                val audioBytes = stopRecordingAudio()
                                if (audioBytes != null && audioBytes.isNotEmpty()) {
                                    AppLogger.i(TAG, "Recording stopped, ${audioBytes.size} bytes -> sending to AudioTranscriber")
                                    // Remove placeholder message
                                    messages = messages.filter { !it.text.startsWith("[🎤 Audio") }
                                    val placeholderBot = ChatMessage(text = "", isUser = false)
                                    messages = messages + placeholderBot

                                    audioTranscriber?.transcribe(
                                        audioBytes = audioBytes,
                                        onToken = { token ->
                                            AppLogger.d(TAG, "[AUDIO-TRANSCRIBE] $token")
                                            mainHandler.post {
                                                val lastBot = messages.indexOfLast { !it.isUser }
                                                if (lastBot >= 0) {
                                                    messages = messages.mapIndexed { idx, msg ->
                                                        if (idx == lastBot) msg.copy(text = msg.text + token) else msg
                                                    }
                                                }
                                            }
                                        },
                                        onDone = {
                                            AppLogger.i(TAG, "[AUDIO-TRANSCRIBE-DONE]")
                                            mainHandler.post {
                                                val transcription = messages.indexOfLast { !it.isUser }.takeIf { it >= 0 }?.let { messages[it].text } ?: ""
                                                AppLogger.i(TAG, "Transcription: '$transcription'")
                                                // Remove placeholder messages (both the 🎤 text and the transcription-accumulated bot msg)
                                                messages = messages.filter { !it.text.startsWith("[🎤 Audio") && it.text != transcription }
                                                // Add user message with transcription
                                                messages = messages + ChatMessage(text = transcription, isUser = true)
                                                // Add bot response placeholder
                                                messages = messages + ChatMessage(text = "", isUser = false)
                                                val textStartTime = System.currentTimeMillis()
                                                AppLogger.i(TAG, "Transcription added as user msg: '$transcription', sending to chat model...")
                                                LlmChatModelHelper.sendMessage(
                                                    message = transcription,
                                                    onToken = { token ->
                                                        AppLogger.d(TAG, "[AUDIO-RESP-TOKEN] $token")
                                                        mainHandler.post {
                                                            val lastBot = messages.indexOfLast { !it.isUser }
                                                            if (lastBot >= 0) {
                                                                messages = messages.mapIndexed { idx, msg ->
                                                                    if (idx == lastBot) msg.copy(text = msg.text + token) else msg
                                                                }
                                                            }
                                                        }
                                                    },
                                                    onDone = {
                                                        AppLogger.i(TAG, "[AUDIO-RESP-DONE]")
                                                        mainHandler.post {
                                                            memoryInfo = LlmChatModelHelper.getMemoryUsage()
                                                            systemMemoryInfo = LlmChatModelHelper.getSystemMemory(context)
                                                            val lastBot = messages.indexOfLast { !it.isUser }
                                                            if (lastBot >= 0) {
                                                                val text = messages[lastBot].text
                                                                val duration = System.currentTimeMillis() - textStartTime
                                                                val tp = if (duration > 0) text.length * 1000f / duration else 0f
                                                                throughput = tp
                                                                messages = messages.mapIndexed { idx, msg ->
                                                                    if (idx == lastBot) msg.copy(throughput = tp, tokenCount = text.length, durationMs = duration) else msg
                                                                }
                                                            }
                                                            currentConversationId?.let { convId ->
                                                                scope.launch {
                                                                    val conv = Conversation(
                                                                        id = convId,
                                                                        title = messages.firstOrNull { it.isUser }?.text?.take(30) ?: "Audio",
                                                                        messages = messages.toList(),
                                                                        createdAt = System.currentTimeMillis(),
                                                                        updatedAt = System.currentTimeMillis()
                                                                    )
                                                                    ChatHistoryManager.saveConversation(context, conv)
                                                                }
                                                            }
                                                        }
                                                    },
                                                    onError = { error ->
                                                        AppLogger.e(TAG, "Audio model response error: ${error.message}", error)
                                                        mainHandler.post {
                                                            val lastBot = messages.indexOfLast { !it.isUser }
                                                            if (lastBot >= 0) {
                                                                messages = messages.mapIndexed { idx, msg ->
                                                                    if (idx == lastBot) msg.copy(text = "Erro: ${error.message}") else msg
                                                                }
                                                            }
                                                        }
                                                    }
                                                )
                                            }
                                        },
                                        onError = { error ->
                                            AppLogger.e(TAG, "AudioTranscriber error: ${error.message}", error)
                                            mainHandler.post {
                                                messages = messages.filter { !it.text.startsWith("[🎤 Audio") }
                                                val lastBot = messages.indexOfLast { !it.isUser }
                                                if (lastBot >= 0) {
                                                    messages = messages.mapIndexed { idx, msg ->
                                                        if (idx == lastBot) msg.copy(text = "Erro audio: ${error.message}") else msg
                                                    }
                                                }
                                            }
                                        }
                                    )
                                }
                            } else {
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                                    == PackageManager.PERMISSION_GRANTED
                                ) {
                                    startRecordingAudio()
                                } else {
                                    recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            }
                        },
                        enabled = isModelReady
                    ) {
                        Icon(
                            imageVector = if (isRecording) Icons.Filled.Stop else Icons.Filled.Mic,
                            contentDescription = if (isRecording) "Stop recording" else "Record voice",
                            tint = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    IconButton(
                        onClick = {
                            AppLogger.i(TAG, "PDF ATTACH button clicked")
                            // Launch document picker for PDF files
                            val intent = android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT).apply {
                                addCategory(android.content.Intent.CATEGORY_OPENABLE)
                                type = "application/pdf"
                            }
                            pdfPickerLauncher.launch(arrayOf("application/pdf"))
                        },
                        enabled = isModelReady
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AttachFile,
                            contentDescription = "Attach PDF",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Button(
                        onClick = {
                            val text = inputText
                            if (text.isBlank() || !isModelReady) return@Button

                            // File read tool commands
                            if (text.startsWith("/read ") || text.startsWith("/file ") || text.startsWith("/ls ")) {
                                val args = text.removePrefix("/read ").removePrefix("/file ").removePrefix("/ls ")
                                val parts = args.split(" -- ")
                                val path = parts[0].trim()
                                var limit = 0; var offset = 1
                                for (i in 1 until parts.size) {
                                    val p = parts[i].trim()
                                    if (p.startsWith("limit ")) limit = p.removePrefix("limit ").trim().toIntOrNull() ?: 0
                                    else if (p.startsWith("offset ")) offset = p.removePrefix("offset ").trim().toIntOrNull() ?: 1
                                }
                                val result = if (path.startsWith("/")) FileReadTool.read(path, limit, offset) else FileReadTool.readPath(path, context)
                                inputText = ""
                                messages = messages + ChatMessage(text = result, isUser = true)
                                return@Button
                            }

                            if (text.startsWith("/grep ")) {
                                val args = text.removePrefix("/grep ")
                                val parts = args.split(" -- ", limit = 2)
                                val path = parts.getOrElse(0) { "" }
                                val pattern = parts.getOrElse(1) { "" }
                                if (path.isNotEmpty() && pattern.isNotEmpty()) {
                                    val result = FileReadTool.grep(path, pattern)
                                    inputText = ""
                                    messages = messages + ChatMessage(text = result, isUser = true)
                                }
                                return@Button
                            }

                            // Normal text message
                            inputText = ""
                            messages = messages + ChatMessage(text = text, isUser = true)
                            val startTime = System.currentTimeMillis()
                            messages = messages + ChatMessage(text = "", isUser = false)
                            LlmChatModelHelper.sendMessage(
                                message = text,
                                onToken = { token -> mainHandler.post {
                                    val lastBotIdx = messages.indexOfLast { !it.isUser }
                                    if (lastBotIdx >= 0) messages = messages.mapIndexed { idx, msg -> if (idx == lastBotIdx) msg.copy(text = msg.text + token) else msg }
                                } },
                                onDone = { mainHandler.post {
                                    val lastBotIdx = messages.indexOfLast { !it.isUser }
                                    if (lastBotIdx >= 0) {
                                        val tokenCount = messages[lastBotIdx].text.length
                                        val duration = System.currentTimeMillis() - startTime
                                        val tp = if (duration > 0) (tokenCount * 1000f) / duration else 0f
                                        throughput = tp
                                        messages = messages.mapIndexed { idx, msg -> if (idx == lastBotIdx) msg.copy(throughput = tp, tokenCount = tokenCount, durationMs = duration) else msg }
                                    }
                                    memoryInfo = LlmChatModelHelper.getMemoryUsage()
                                    systemMemoryInfo = LlmChatModelHelper.getSystemMemory(context)
                                    currentConversationId?.let { convId ->
                                        scope.launch {
                                            val conv = Conversation(id = convId, title = messages.firstOrNull { it.isUser }?.text?.take(30) ?: "Nova conversa", messages = messages, createdAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis())
                                            ChatHistoryManager.saveConversation(context, conv)
                                        }
                                    }
                                } },
                                onError = { error -> mainHandler.post {
                                    val lastBotIdx = messages.indexOfLast { !it.isUser }
                                    if (lastBotIdx >= 0) messages = messages.mapIndexed { idx, msg -> if (idx == lastBotIdx) msg.copy(text = "Erro: ${error.message}") else msg }
                                } }
                            )
                        },
                        enabled = isModelReady && inputText.isNotBlank()
                    ) {
                        Text("Send")
                    }
                }
            }
        }
    }

        // Send message handler - extracted for reuse by keyboard send action
        fun handleSendMessage() {
            AppLogger.i(TAG, "SEND BUTTON CLICKED isModelReady=$isModelReady inputBlank=${inputText.isBlank()}")
            if (inputText.isBlank() || !isModelReady) {
                AppLogger.i(TAG, "SEND BLOCKED: blank=$inputText.isBlank() ready=$isModelReady")
                return
            }

            AppLogger.d(TAG, "User sent: $inputText")
            val userMessageText = inputText

            // Workspace commands
            if (userMessageText == "/workspace") {
                val result = WorkspaceManager.listWorkspace(context)
                inputText = ""
                val toolMsg = ChatMessage(text = result, isUser = true)
                messages = messages + toolMsg
                return
            }

            if (userMessageText == "/ls-workspace") {
                val result = WorkspaceManager.listMarkdown(context)
                inputText = ""
                val toolMsg = ChatMessage(text = result, isUser = true)
                messages = messages + toolMsg
                return
            }

            // Check if it's a tool command
            if (userMessageText.startsWith("/read ") || userMessageText.startsWith("/file ") || userMessageText.startsWith("/ls ")) {
                // File read tool command
                val args = userMessageText.removePrefix("/read ").removePrefix("/file ").removePrefix("/ls ")
                val parts = args.split(" -- ")
                val path = parts[0].trim()
                var limit = 0; var offset = 1
                for (i in 1 until parts.size) {
                    val p = parts[i].trim()
                    if (p.startsWith("limit ")) limit = p.removePrefix("limit ").trim().toIntOrNull() ?: 0
                    else if (p.startsWith("offset ")) offset = p.removePrefix("offset ").trim().toIntOrNull() ?: 1
                }
                val result = if (path.startsWith("/")) {
                    FileReadTool.read(path, limit, offset)
                } else {
                    FileReadTool.readPath(path, context)
                }
                inputText = ""
                val toolMsg = ChatMessage(text = result, isUser = true)
                messages = messages + toolMsg
                return
            }

            if (userMessageText.startsWith("/grep ")) {
                val args = userMessageText.removePrefix("/grep ")
                val parts = args.split(" -- ", limit = 2)
                val path = parts.getOrElse(0) { "" }
                val pattern = parts.getOrElse(1) { "" }
                if (path.isNotEmpty() && pattern.isNotEmpty()) {
                    val result = FileReadTool.grep(path, pattern)
                    inputText = ""
                    val toolMsg = ChatMessage(text = result, isUser = true)
                    messages = messages + toolMsg
                    return
                }
            }

            inputText = ""
            val userMessage = ChatMessage(text = userMessageText, isUser = true)
            messages = messages + userMessage

            val startTime = System.currentTimeMillis()
            messages = messages + ChatMessage(text = "", isUser = false)

            LlmChatModelHelper.sendMessage(
                message = userMessageText,
                onToken = { token ->
                    AppLogger.d(TAG, "Token: $token")
                    mainHandler.post {
                        val lastBotIdx = messages.indexOfLast { !it.isUser }
                        AppLogger.d(TAG, "onToken: lastBotIdx=$lastBotIdx messages=${messages.size}")
                        if (lastBotIdx >= 0) {
                            val currentMsg = messages[lastBotIdx]
                            messages = messages.mapIndexed { idx, msg ->
                                if (idx == lastBotIdx) {
                                    msg.copy(text = msg.text + token)
                                } else msg
                            }
                        }
                    }
                },
                onDone = {
                    AppLogger.i(TAG, "onDone callback fired")
                    mainHandler.post {
                        AppLogger.i(TAG, "onDone mainHandler.post running")
                        memoryInfo = LlmChatModelHelper.getMemoryUsage()
                        systemMemoryInfo = LlmChatModelHelper.getSystemMemory(context)
                        val lastBotIdx = messages.indexOfLast { !it.isUser }
                        AppLogger.i(TAG, "onDone: lastBotIdx=$lastBotIdx totalMessages=${messages.size}")
                        if (lastBotIdx >= 0) {
                            val tokenCount = messages[lastBotIdx].text.length
                            val duration = System.currentTimeMillis() - startTime
                            val tp = if (duration > 0) (tokenCount * 1000f) / duration else 0f
                            AppLogger.i(TAG, "Throughput: tp=$tp count=$tokenCount duration=$duration")
                            throughput = tp
                            messages = messages.mapIndexed { idx, msg ->
                                if (idx == lastBotIdx) {
                                    msg.copy(throughput = tp, tokenCount = tokenCount, durationMs = duration)
                                } else msg
                            }
                            AppLogger.i(TAG, "onDone: metrics attached to message idx=$lastBotIdx tp=$tp")
                            // Auto-save conversation after response
                            currentConversationId?.let { convId ->
                                scope.launch {
                                    val conv = Conversation(
                                        id = convId,
                                        title = messages.firstOrNull { it.isUser }?.text?.take(30) ?: "Nova conversa",
                                        messages = messages,
                                        createdAt = System.currentTimeMillis(),
                                        updatedAt = System.currentTimeMillis()
                                    )
                                    ChatHistoryManager.saveConversation(context, conv)
                                }
                            }
                        }
                    }
                },
                onError = { error ->
                    AppLogger.e(TAG, "Error: ${error.message}", error)
                    scope.launch {
                        snackbarHostState.showSnackbar("Error: ${error.message}")
                    }
                }
            )
        }

        // Settings Dialog
        if (showSettings) {
            SettingsDialog(
                settings = settings,
                onSettingsChange = { newSettings ->
                    settings = newSettings
                    scope.launch {
                        LlmPreferences.saveSettings(context, newSettings)
                    }
                },
                onReload = {
                    isReloading = true
                    messages = emptyList()
                    initStage = "Recarregando..."
                    initProgress = 0f
                    isInitializing = true
                    scope.launch {
                        val params = LlmPreferences.settingsToLlmParams(settings)
                        LlmChatModelHelper.reload(params) { stage, progress ->
                            mainHandler.post {
                                initStage = stage
                                initProgress = progress / 100f
                            }
                        }
                        isInitializing = false
                        isReloading = false
                        isModelReady = true
                        initStage = "Pronto!"
                        initProgress = 1f
                        memoryInfo = LlmChatModelHelper.getMemoryUsage()
                        systemMemoryInfo = LlmChatModelHelper.getSystemMemory(context)
                        snackbarHostState.showSnackbar("LLM recarregado com novos parametros!")
                    }
                },
                onDismiss = { showSettings = false }
            )
        }
    }
}

@Composable
fun ChatBubble(message: ChatMessage, showMetrics: Boolean = false) {
    val isUser = message.isUser
    val backgroundColor = if (isUser) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val textColor = if (isUser) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Column(
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            Box(
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .background(
                        color = backgroundColor,
                        shape = RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomStart = if (isUser) 16.dp else 4.dp,
                            bottomEnd = if (isUser) 4.dp else 16.dp
                        )
                    )
                    .padding(12.dp)
            ) {
                if (isUser) {
                    Text(
                        text = message.text,
                        color = textColor,
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    MarkdownText(
                        text = message.text,
                        color = textColor
                    )
                }
            }
            if (showMetrics && !isUser && message.throughput > 0) {
                Text(
                    text = "${String.format("%.1f", message.throughput)} tok/s • ${message.tokenCount} tokens • ${message.durationMs}ms",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp, start = 4.dp, end = 4.dp)
                )
            }
        }
    }
}

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val text: String,
    val isUser: Boolean,
    val throughput: Float = 0f,
    val tokenCount: Int = 0,
    val durationMs: Long = 0L
)

@Composable
fun MarkdownText(text: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    val themeColor = MaterialTheme.colorScheme.primary
    val parsed = remember(text) {
        buildAnnotatedString {
            var i = 0
            val s = text
            while (i < s.length) {
                // Bold **text**
                if (s.startsWith("**", i)) {
                    val end = s.indexOf("**", i + 2)
                    if (end != -1) {
                        withStyle(style = androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(s.substring(i + 2, end))
                        }
                        i = end + 2
                    } else { append(s[i]); i++ }
                }
                // Italic *text* (but not **)
                else if (s.startsWith("*", i) && !s.startsWith("**", i)) {
                    val end = s.indexOf("*", i + 1)
                    if (end != -1 && end < s.length && s[end - 1] != '*') {
                        withStyle(style = androidx.compose.ui.text.SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)) {
                            append(s.substring(i + 1, end))
                        }
                        i = end + 1
                    } else { append(s[i]); i++ }
                }
                // Inline `code`
                else if (s.startsWith("`", i)) {
                    val end = s.indexOf("`", i + 1)
                    if (end != -1) {
                        withStyle(style = androidx.compose.ui.text.SpanStyle(fontFamily = FontFamily.Monospace, color = themeColor)) {
                            append(s.substring(i + 1, end))
                        }
                        i = end + 1
                    } else { append(s[i]); i++ }
                }
                // Line starting with •
                else if (s.startsWith("• ", i)) {
                    append("  • ")
                    i += 2
                }
                // Line starting with number and dot
                else if (i + 2 < s.length && s[i].isDigit() && s[i + 1] == '.') {
                    append(s.substring(i, i + 2))
                    i += 2
                }
                // \n\n creates paragraph break
                else if (s.startsWith("\n\n", i)) {
                    append("\n")
                    i += 2
                }
                // Regular character
                else {
                    append(s[i])
                    i++
                }
            }
        }
    }
    Text(
        text = parsed,
        color = color,
        style = MaterialTheme.typography.bodyMedium,
        modifier = modifier
    )
}

@Composable
fun SettingsDialog(
    settings: LlmPreferences.Settings,
    onSettingsChange: (LlmPreferences.Settings) -> Unit,
    onReload: () -> Unit,
    onDismiss: () -> Unit
) {
    var maxTokens by remember { mutableStateOf(settings.maxTokens.toFloat()) }
    var temperature by remember { mutableStateOf(settings.temperature) }
    var topK by remember { mutableStateOf(settings.topK.toFloat()) }
    var topP by remember { mutableStateOf(settings.topP) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("LLM Settings") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Max Tokens
                Text("Max Context Tokens: ${maxTokens.toInt()}")
                Slider(
                    value = maxTokens,
                    onValueChange = { maxTokens = it },
                    valueRange = 256f..8192f,
                    steps = 30,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Temperature
                Text("Temperature: ${String.format("%.2f", temperature)}")
                Slider(
                    value = temperature,
                    onValueChange = { temperature = it },
                    valueRange = 0.1f..2.0f,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Top K
                Text("Top-K: ${topK.toInt()}")
                Slider(
                    value = topK,
                    onValueChange = { topK = it },
                    valueRange = 1f..100f,
                    steps = 98,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Top P
                Text("Top-P: ${String.format("%.2f", topP)}")
                Slider(
                    value = topP,
                    onValueChange = { topP = it },
                    valueRange = 0.5f..1.0f,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val newSettings = LlmPreferences.Settings(
                        maxTokens = maxTokens.toInt(),
                        temperature = temperature,
                        topK = topK.toInt(),
                        topP = topP
                    )
                    onSettingsChange(newSettings)
                    onReload()
                    onDismiss()
                }
            ) {
                Text("Reload LLM")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
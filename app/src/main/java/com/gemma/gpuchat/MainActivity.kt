@file:OptIn(ExperimentalMaterial3Api::class)

package com.gemma.gpuchat

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "GemmaApp"

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            AppLogger.init(this)
            AppLogger.d(TAG, "=== onCreate() called ===")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Logger init failed", e)
        }
        Toast.makeText(this, "App started!", Toast.LENGTH_SHORT).show()
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
    var lastResponseTokenCount by remember { mutableStateOf(0) }
    var lastResponseDurationMs by remember { mutableStateOf(0L) }
    var responseStartTime by remember { mutableStateOf(0L) }

    // Settings dialog state
    var showSettings by remember { mutableStateOf(false) }
    var settings by remember { mutableStateOf(LlmPreferences.Settings()) }
    var isReloading by remember { mutableStateOf(false) }

    // Handler for UI thread updates from background callbacks
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    // Load saved settings
    LaunchedEffect(Unit) {
        settings = LlmPreferences.getSettingsFlow(context).first()
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

            // Run initialization on IO thread with UI-safe callbacks
            val params = LlmPreferences.settingsToLlmParams(settings)
            withContext(Dispatchers.IO) {
                LlmChatModelHelper.initialize(context, modelPath, params) { stage, progress ->
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
            memoryInfo = LlmChatModelHelper.getMemoryUsage()
            systemMemoryInfo = LlmChatModelHelper.getSystemMemory(context)
            initStage = "Pronto!"
            initProgress = 1f
            AppLogger.i(TAG, "Model initialized successfully!")
            Toast.makeText(context, "Model ready!", Toast.LENGTH_SHORT).show()
            snackbarHostState.showSnackbar("Modelo pronto!")
        } catch (e: Exception) {
            isInitializing = false
            AppLogger.e(TAG, "Model init failed", e)
            initStage = "Erro: ${e.message}"
            errorMessage = "Model init failed: ${e.message}"
            snackbarHostState.showSnackbar("Model init failed: ${e.message}")
            Toast.makeText(context, "Model init failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // Helper to find last bot message index safely
    fun getLastBotMessageIndex(msgs: List<ChatMessage>): Int {
        return msgs.indexOfLast { !it.isUser }
    }

    // Auto message state: 0=none, 1="Olá", 2="Qual a sua LLM", 3="O que voce sabe fazer", 4="Se apresente"
    var autoMessageState by remember { mutableStateOf(0) }

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

    // Auto-send "Olá" when model becomes ready
    LaunchedEffect(isModelReady) {
        if (isModelReady && autoMessageState == 0) {
            AppLogger.i(TAG, "Model ready - auto-sending Olá")
            autoMessageState = 1

            val userMessage = ChatMessage(text = "Olá", isUser = true)
            messages = messages + userMessage

            val botMsg = ChatMessage(text = "", isUser = false)
            messages = messages + botMsg

            val startTime = System.currentTimeMillis()
            LlmChatModelHelper.sendMessage(
                message = userMessage.text,
                onToken = { token ->
                    AppLogger.d(TAG, "[OLA-RESPONSE-TOKEN] $token")
                    messages = messages.mapIndexed { index, msg ->
                        if (index == getLastBotMessageIndex(messages) && !msg.isUser) {
                            msg.copy(text = msg.text + token)
                        } else msg
                    }
                },
                onDone = {
                    AppLogger.i(TAG, "[OLA-RESPONSE-DONE] Response complete")
                    val lastBotIdx = messages.indexOfLast { !it.isUser }
                    if (lastBotIdx >= 0) {
                        val tokenCount = messages[lastBotIdx].text.length
                        val duration = System.currentTimeMillis() - startTime
                        val tp = if (duration > 0) (tokenCount * 1000f) / duration else 0f
                        throughput = tp
                        AppLogger.i(TAG, "[OLA-RESPONSE-THROUGHPUT] tk/s=$tp count=$tokenCount dur=$duration")
                        messages = messages.mapIndexed { idx, msg ->
                            if (idx == lastBotIdx) {
                                msg.copy(throughput = tp, tokenCount = tokenCount, durationMs = duration)
                            } else msg
                        }
                    }
                    // Now send "Qual a sua LLM"
                    sendAutoMessage("Qual a sua LLM", 2) {
                        // Now send "O que você sabe fazer"
                        sendAutoMessage("O que você sabe fazer?", 3) {
                            // Now send "Se apresente"
                            sendAutoMessage("Se apresente", 4) {
                                AppLogger.i(TAG, "All 4 auto messages complete")
                            }
                        }
                    }
                },
                onError = { error ->
                    AppLogger.e(TAG, "[OLA-RESPONSE-ERROR] ${error.message}", error)
                }
            )
        }
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
                            HorizontalDivider(
                                modifier = Modifier
                                    .height(16.dp)
                                    .width(1.dp),
                                color = MaterialTheme.colorScheme.outline
                            )
                            Text(
                                text = "tk/s: ${String.format("%.1f", throughput)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Settings",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = {
                        logContent = AppLogger.readLogFile()
                        showLogs = !showLogs
                    }) {
                        if (showLogs) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "Hide Logs",
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "Show Logs",
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
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
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = initStage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { initProgress },
                        modifier = Modifier.fillMaxWidth(0.8f),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${(initProgress * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    memoryInfo?.let { mem ->
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Modelo: ${mem.modelSizeMb} MB",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "App: ${mem.appUsedMb} MB | Native: ${mem.nativeHeapMb} MB",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
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
                    Button(
                        onClick = {
                            AppLogger.i(TAG, "SEND BUTTON CLICKED isModelReady=$isModelReady inputBlank=${inputText.isBlank()}")
                            if (inputText.isBlank() || !isModelReady) {
                                AppLogger.i(TAG, "SEND BLOCKED: blank=$inputText.isBlank() ready=$isModelReady")
                                return@Button
                            }

                            AppLogger.d(TAG, "User sent: $inputText")
                            val userMessage = ChatMessage(
                                text = inputText,
                                isUser = true
                            )
                            messages = messages + userMessage
                            inputText = ""

                            val startTime = System.currentTimeMillis()
                            messages = messages + ChatMessage(
                                text = "",
                                isUser = false
                            )

                            LlmChatModelHelper.sendMessage(
                                message = userMessage.text,
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
                        },
                        enabled = isModelReady && inputText.isNotBlank()
                    ) {
                        Text("Send")
                    }
                }
            }
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
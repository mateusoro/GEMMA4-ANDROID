@file:OptIn(ExperimentalMaterial3Api::class)

package com.gemma.gpuchat

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
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

    // Handler for UI thread updates from background callbacks
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    // Initialize model
    LaunchedEffect(Unit) {
        AppLogger.d(TAG, "LaunchedEffect started - initializing model")
        isInitializing = true
        initStage = "Procurando modelo..."
        initProgress = 0f

        try {
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
            withContext(Dispatchers.IO) {
                LlmChatModelHelper.initialize(context, modelPath) { stage, progress ->
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
                title = { Text("Gemma 4 GPU Chat") },
                actions = {
                    Button(onClick = {
                        logContent = AppLogger.readLogFile()
                        showLogs = !showLogs
                    }) {
                        Text(if (showLogs) "Hide Logs" else "Show Logs")
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
                            text = "Memória app: ${mem.appUsedMb} MB / ${mem.appTotalMb} MB",
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
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item { Spacer(modifier = Modifier.height(8.dp)) }
                    items(messages, key = { it.id }) { message ->
                        ChatBubble(message = message)
                    }
                    item { Spacer(modifier = Modifier.height(8.dp)) }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
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
                            if (inputText.isBlank() || !isModelReady) return@Button

                            AppLogger.d(TAG, "User sent: $inputText")
                            val userMessage = ChatMessage(
                                text = inputText,
                                isUser = true
                            )
                            messages = messages + userMessage
                            inputText = ""

                            var modelResponseId = ""
                            messages = messages + ChatMessage(
                                id = modelResponseId,
                                text = "",
                                isUser = false
                            )

                            LlmChatModelHelper.sendMessage(
                                message = userMessage.text,
                                onToken = { token ->
                                    AppLogger.d(TAG, "Token: $token")
                                    messages = messages.mapIndexed { index, msg ->
                                        if (index == messages.lastIndex && !msg.isUser) {
                                            modelResponseId = msg.id
                                            msg.copy(text = msg.text + token)
                                        } else msg
                                    }
                                },
                                onDone = {
                                    AppLogger.i(TAG, "Response complete")
                                    scope.launch {
                                        snackbarHostState.showSnackbar("Response complete")
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
    }
}

@Composable
fun ChatBubble(message: ChatMessage) {
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
            Text(
                text = message.text,
                color = textColor,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val text: String,
    val isUser: Boolean
)
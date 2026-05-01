package com.gemma.gpuchat

import android.app.ActivityManager
import android.content.Context
import android.os.Debug
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import java.io.File

@OptIn(ExperimentalApi::class)
object LlmChatModelHelper {
    private var engine: Engine? = null
    private var conversation: Conversation? = null
    private val TAG = "LlmChatModelHelper"

    // Memory info
    private var loadedModelSizeBytes: Long = 0L

    // LLM Parameters
    data class LlmParams(
        val maxNumTokens: Int = 2048,
        val temperature: Float = 0.8f,
        val topK: Int = 10,
        val topP: Float = 0.95f
    )

    private var currentParams: LlmParams = LlmParams()
    private var currentModelPath: String = ""
    private var currentContext: Context? = null

    fun getParams(): LlmParams = currentParams

    fun updateParams(params: LlmParams) {
        currentParams = params
        AppLogger.d(TAG, "Params updated: maxTokens=${params.maxNumTokens}, temp=${params.temperature}, topK=${params.topK}, topP=${params.topP}")
    }

    fun getLoadedModelSizeBytes(): Long = loadedModelSizeBytes

    fun getMemoryUsage(): MemoryInfo {
        val runtime = Runtime.getRuntime()
        val totalMem = runtime.totalMemory()
        val freeMem = runtime.freeMemory()
        val usedMem = totalMem - freeMem
        val maxMem = runtime.maxMemory()
        val nativeHeap = Debug.getNativeHeapAllocatedSize()
        return MemoryInfo(
            appUsedMb = usedMem / (1024 * 1024),
            appTotalMb = maxMem / (1024 * 1024),
            modelSizeMb = loadedModelSizeBytes / (1024 * 1024),
            nativeHeapMb = nativeHeap / (1024 * 1024)
        )
    }

    fun getSystemMemory(context: Context): SystemMemoryInfo {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        return SystemMemoryInfo(
            totalMb = memInfo.totalMem / (1024 * 1024),
            availableMb = memInfo.availMem / (1024 * 1024),
            usedMb = memInfo.totalMem / (1024 * 1024) - memInfo.availMem / (1024 * 1024)
        )
    }

    data class MemoryInfo(
        val appUsedMb: Long,
        val appTotalMb: Long,
        val modelSizeMb: Long,
        val nativeHeapMb: Long
    )

    data class SystemMemoryInfo(
        val totalMb: Long,
        val availableMb: Long,
        val usedMb: Long
    )

    fun initialize(
        context: Context,
        modelPath: String,
        params: LlmParams = LlmParams(),
        onProgress: (String, Int) -> Unit = { _, _ -> }
    ) {
        currentParams = params
        currentModelPath = modelPath
        currentContext = context
        AppLogger.d(TAG, ">>> initialize() CALLED <<<")
        AppLogger.d(TAG, "modelPath: $modelPath")
        AppLogger.d(TAG, "params: maxTokens=${params.maxNumTokens}, temp=${params.temperature}, topK=${params.topK}, topP=${params.topP}")
        AppLogger.d(TAG, "filesDir: ${context.filesDir}")

        onProgress("Procurando modelo...", 0)

        // Get model file size
        try {
            val modelFile = File(modelPath)
            if (modelFile.exists()) {
                loadedModelSizeBytes = modelFile.length()
                AppLogger.d(TAG, "Model file size: ${loadedModelSizeBytes} bytes (${loadedModelSizeBytes / (1024*1024)} MB)")
            }
        } catch (e: Exception) {
            AppLogger.w(TAG, "Could not get model file size: ${e.message}")
        }

        // Try GPU first
        try {
            AppLogger.d(TAG, "Trying GPU backend...")
            onProgress("Iniciando GPU backend...", 10)
            initializeWithBackend(context, modelPath, Backend.GPU(), onProgress)
            AppLogger.i(TAG, "GPU backend SUCCESS!")
            onProgress("Modelo carregado com sucesso!", 100)
            return
        } catch (e: Exception) {
            val msg = e.message ?: ""
            AppLogger.w(TAG, "GPU failed: $msg")
            if (msg.contains("PERMISSION_DENIED") || msg.contains("Permission denied") || msg.contains("open() failed")) {
                AppLogger.w(TAG, "GPU permission denied - falling back to CPU")
            }
        }

        // Fallback: try CPU
        try {
            AppLogger.d(TAG, "Trying CPU backend...")
            onProgress("GPU falhou, tentando CPU...", 50)
            initializeWithBackend(context, modelPath, Backend.CPU(), onProgress)
            AppLogger.i(TAG, "CPU backend SUCCESS (fallback)!")
            onProgress("Modelo carregado (CPU fallback)!", 100)
        } catch (e: Exception) {
            AppLogger.e(TAG, "CPU also failed: ${e.message}", e)
            throw e
        }
    }

    private fun initializeWithBackend(
        context: Context,
        modelPath: String,
        backend: Backend,
        onProgress: (String, Int) -> Unit = { _, _ -> }
    ) {
        AppLogger.d(TAG, "Backend: $backend (maxNumTokens=2048)")

        // If path is on sdcard, try to copy to internal storage for native access
        var actualPath: String = modelPath
        if (modelPath.startsWith("/sdcard")) {
            AppLogger.d(TAG, "Model on sdcard - attempting copy to internal storage...")
            try {
                val modelFile = File(modelPath)
                val internalFile = File(context.filesDir, modelFile.name)
                if (!internalFile.exists() || internalFile.length() != modelFile.length()) {
                    AppLogger.d(TAG, "Copying ${modelFile.length()} bytes to ${internalFile.absolutePath}...")
                    modelFile.inputStream().use { input ->
                        internalFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    AppLogger.i(TAG, "Copy complete: ${internalFile.length()} bytes")
                } else {
                    AppLogger.d(TAG, "Internal copy already exists")
                }
                actualPath = internalFile.absolutePath
            } catch (e: Exception) {
                AppLogger.w(TAG, "Copy failed: ${e.message}, using original path")
                actualPath = modelPath
            }
        } else {
            actualPath = modelPath
        }

        AppLogger.d(TAG, "EngineConfig created with path: $actualPath")
        AppLogger.d(TAG, "Creating Engine instance...")
        AppLogger.d(TAG, "[PROGRESS] Criando engine... (30%)")
        onProgress("Criando engine...", 30)

        val engineConfig = EngineConfig(
            modelPath = actualPath,
            backend = backend,
            maxNumTokens = currentParams.maxNumTokens,
            audioBackend = Backend.CPU(),
            maxNumImages = 4
        )
        engine = Engine(engineConfig)
        AppLogger.d(TAG, "Engine instance created, calling initialize()...")
        AppLogger.d(TAG, "[PROGRESS] Inicializando modelo (pode levar 10-20s)... (40%)")
        onProgress("Inicializando modelo (pode levar 10-20s)...", 40)

        engine!!.initialize()
        AppLogger.d(TAG, "engine.initialize() returned successfully")
        onProgress("Engine initialized, criando conversa...", 70)

        AppLogger.d(TAG, "Creating conversation...")
        val samplerConfig = SamplerConfig(
            topK = currentParams.topK,
            topP = currentParams.topP.toDouble(),
            temperature = currentParams.temperature.toDouble()
        )
        val convConfig = ConversationConfig(samplerConfig = samplerConfig)
        conversation = engine!!.createConversation(convConfig)
        AppLogger.d(TAG, "Conversation created: $conversation with sampler topK=${currentParams.topK}, topP=${currentParams.topP}, temp=${currentParams.temperature}")
        AppLogger.d(TAG, "[PROGRESS] Conversa pronta! (90%)")
        onProgress("Conversa pronta!", 90)

        AppLogger.i(TAG, ">>> INITIALIZATION COMPLETE (backend=$backend) <<<")
    }

    fun sendMessage(
        message: String,
        onToken: (String) -> Unit,
        onDone: () -> Unit,
        onError: (Throwable) -> Unit
    ) {
        AppLogger.d(TAG, ">>> sendMessage() CALLED <<<")
        AppLogger.d(TAG, "message: $message")
        AppLogger.d(TAG, "conversation: $conversation")

        if (conversation == null) {
            AppLogger.e(TAG, "conversation is NULL!")
            onError(IllegalStateException("Conversation not initialized"))
            return
        }

        val callback = object : MessageCallback {
            private var done = false

            override fun onMessage(message: Message) {
                if (done) {
                    AppLogger.w(TAG, "onMessage received after onDone - ignoring")
                    return
                }
                val text = message.toString()
                AppLogger.d(TAG, "onMessage: '$text'")
                try {
                    onToken(text)
                } catch (e: Exception) {
                    AppLogger.e(TAG, "onToken callback threw: ${e.message}", e)
                }
            }

            override fun onDone() {
                if (done) {
                    AppLogger.w(TAG, "onDone called twice - ignoring")
                    return
                }
                done = true
                AppLogger.i(TAG, "onDone")
                try {
                    onDone()
                } catch (e: Exception) {
                    AppLogger.e(TAG, "onDone callback threw: ${e.message}", e)
                }
            }

            override fun onError(throwable: Throwable) {
                AppLogger.e(TAG, "onError: ${throwable.message}", throwable)
                try {
                    onError(throwable)
                } catch (e: Exception) {
                    AppLogger.e(TAG, "onError callback threw: ${e.message}", e)
                }
            }
        }

        AppLogger.d(TAG, "Calling conversation.sendMessageAsync()...")
        conversation?.sendMessageAsync(
            Contents.of(message),
            callback
        )
        AppLogger.d(TAG, "sendMessageAsync() returned (async)")
    }

    fun sendAudioMessage(
        audioBytes: ByteArray,
        onToken: (String) -> Unit,
        onDone: () -> Unit,
        onError: (Throwable) -> Unit
    ) {
        AppLogger.d(TAG, ">>> sendAudioMessage() CALLED <<<")
        AppLogger.d(TAG, "audioBytes size: ${audioBytes.size}")

        if (conversation == null) {
            AppLogger.e(TAG, "conversation is NULL!")
            onError(IllegalStateException("Conversation not initialized"))
            return
        }

        val callback = object : MessageCallback {
            private var done = false

            override fun onMessage(message: Message) {
                if (done) return
                val text = message.toString()
                AppLogger.d(TAG, "onMessage: '$text'")
                try { onToken(text) } catch (e: Exception) { AppLogger.e(TAG, "onToken threw", e) }
            }

            override fun onDone() {
                if (done) return
                done = true
                AppLogger.i(TAG, "onDone")
                try { onDone() } catch (e: Exception) { AppLogger.e(TAG, "onDone threw", e) }
            }

            override fun onError(throwable: Throwable) {
                AppLogger.e(TAG, "onError: ${throwable.message}", throwable)
                try { onError(throwable) } catch (e: Exception) { AppLogger.e(TAG, "onError threw", e) }
            }
        }

        AppLogger.d(TAG, "Sending audio via Contents.of(Content.AudioBytes(...))")
        conversation?.sendMessageAsync(
            Contents.of(
                Content.AudioBytes(audioBytes),
                Content.Text("Please transcribe the speech in this audio.")
            ),
            callback
        )
        AppLogger.d(TAG, "sendMessageAsync(audio) returned (async)")
    }

    fun release() {
        AppLogger.d(TAG, ">>> release() CALLED <<<")
        try {
            conversation?.let {
                AppLogger.d(TAG, "Closing conversation...")
                it.close()
            }
            engine?.let {
                AppLogger.d(TAG, "Closing engine...")
                it.close()
            }
            conversation = null
            engine = null
            AppLogger.i(TAG, "release() complete")
        } catch (e: Exception) {
            AppLogger.e(TAG, "release() error", e)
        }
    }

    fun reload(params: LlmParams, onProgress: (String, Int) -> Unit = { _, _ -> }) {
        AppLogger.d(TAG, ">>> reload() CALLED <<<")
        val ctx = currentContext ?: throw IllegalStateException("No context set - call initialize first")
        val path = currentModelPath
        release()
        currentParams = params
        initialize(ctx, path, params, onProgress)
    }

    fun isInitialized(): Boolean = engine != null && conversation != null
}
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

    private fun createNewConversation(): Conversation? {
        if (engine == null) return null
        val samplerConfig = SamplerConfig(
            topK = currentParams.topK,
            topP = currentParams.topP.toDouble(),
            temperature = currentParams.temperature.toDouble()
        )
        val convConfig = ConversationConfig(samplerConfig = samplerConfig)
        return engine!!.createConversation(convConfig)
    }

    fun resetConversation() {
        conversation?.close()
        conversation = createNewConversation()
        AppLogger.i(TAG, "Conversation reset to fresh state")
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

private fun wrapAudioInWav(audioBytes: ByteArray): ByteArray {
        val sampleRate = 16000
        val numChannels = 1
        val bitsPerSample = 16
        val byteRate = sampleRate * numChannels * bitsPerSample / 8
        val blockAlign = numChannels * bitsPerSample / 8
        val dataSize = audioBytes.size
        val numSamples = dataSize / (bitsPerSample / 8)
        val fileSize = 44 + dataSize

        val wav = ByteArray(56 + dataSize)
        // RIFF header
        wav[0] = 'R'.code.toByte(); wav[1] = 'I'.code.toByte(); wav[2] = 'F'.code.toByte(); wav[3] = 'F'.code.toByte()
        // File size - 8 (little endian)
        wav[4] = (fileSize and 0xFF).toByte(); wav[5] = ((fileSize shr 8) and 0xFF).toByte()
        wav[6] = ((fileSize shr 16) and 0xFF).toByte(); wav[7] = ((fileSize shr 24) and 0xFF).toByte()
        // WAVE
        wav[8] = 'W'.code.toByte(); wav[9] = 'A'.code.toByte(); wav[10] = 'V'.code.toByte(); wav[11] = 'E'.code.toByte()
        // fmt chunk
        wav[12] = 'f'.code.toByte(); wav[13] = 'm'.code.toByte(); wav[14] = 't'.code.toByte(); wav[15] = ' '.code.toByte()
        // fmt chunk size (16 for PCM)
        wav[16] = 16; wav[17] = 0; wav[18] = 0; wav[19] = 0
        // Audio format (1 = PCM)
        wav[20] = 1; wav[21] = 0
        // Number of channels
        wav[22] = numChannels.toByte(); wav[23] = 0
        // Sample rate (little endian)
        wav[24] = (sampleRate and 0xFF).toByte(); wav[25] = ((sampleRate shr 8) and 0xFF).toByte()
        wav[26] = ((sampleRate shr 16) and 0xFF).toByte(); wav[27] = ((sampleRate shr 24) and 0xFF).toByte()
        // Byte rate
        wav[28] = (byteRate and 0xFF).toByte(); wav[29] = ((byteRate shr 8) and 0xFF).toByte()
        wav[30] = ((byteRate shr 16) and 0xFF).toByte(); wav[31] = ((byteRate shr 24) and 0xFF).toByte()
        // Block align
        wav[32] = blockAlign.toByte(); wav[33] = 0
        // Bits per sample
        wav[34] = bitsPerSample.toByte(); wav[35] = 0
        // fact chunk
        wav[36] = 'f'.code.toByte(); wav[37] = 'a'.code.toByte(); wav[38] = 'c'.code.toByte(); wav[39] = 't'.code.toByte()
        // fact chunk size = 4
        wav[40] = 4; wav[41] = 0; wav[42] = 0; wav[43] = 0
        // Number of samples (little endian)
        wav[44] = (numSamples and 0xFF).toByte(); wav[45] = ((numSamples shr 8) and 0xFF).toByte()
        wav[46] = ((numSamples shr 16) and 0xFF).toByte(); wav[47] = ((numSamples shr 24) and 0xFF).toByte()
        // data chunk
        wav[48] = 'd'.code.toByte(); wav[49] = 'a'.code.toByte(); wav[50] = 't'.code.toByte(); wav[51] = 'a'.code.toByte()
        // Data size (little endian)
        wav[52] = (dataSize and 0xFF).toByte(); wav[53] = ((dataSize shr 8) and 0xFF).toByte()
        wav[54] = ((dataSize shr 16) and 0xFF).toByte(); wav[55] = ((dataSize shr 24) and 0xFF).toByte()
        // raw PCM data
        for (i in audioBytes.indices) {
            wav[56 + i] = audioBytes[i]
        }
        return wav
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

AppLogger.d(TAG, "Sending audio as WAV ${wrapAudioInWav(audioBytes).size} bytes (PCM ${audioBytes.size} bytes + 56-byte header with fact chunk)")
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
        conversation?.sendMessageAsync(
            Contents.of(
                Content.AudioBytes(wrapAudioInWav(audioBytes)),
                Content.Text("Transcribe the following speech segment in its original language. Only output the transcription.")
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
package com.gemma.gpuchat

import android.app.ActivityManager
import android.content.Context
import android.os.Debug
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.ToolProvider
import com.google.ai.edge.litertlm.tool
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.Channel
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

    // LLM Parameters — Gemma-4-E2B-IT recommended defaults (per litert-community model card)
    data class LlmParams(
        val maxNumTokens: Int = 2048,
        val temperature: Float = 1.0f,
        val topK: Int = 64,
        val topP: Float = 0.95f
    )

    private var currentParams: LlmParams = LlmParams()
    private var currentModelPath: String = ""
    private var currentContext: Context? = null
    private var currentTools: List<ToolProvider> = emptyList()
    private var currentAgentTools: AgentTools? = null
    private var currentSystemInstruction: Contents? = null
    private var currentChannels: List<Channel>? = null
    private var currentExtraContext: Map<String, Any>? = null

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
        agentTools: AgentTools? = null,
        tools: List<ToolProvider> = emptyList(),
        systemInstruction: Contents? = null,
        channels: List<Channel>? = null,
        extraContext: Map<String, Any>? = null,
        onProgress: (String, Int) -> Unit = { _, _ -> }
    ) {
        currentParams = params
        currentModelPath = modelPath
        currentContext = context
        currentTools = tools
        currentAgentTools = agentTools
        agentToolsInstance = agentTools
        currentSystemInstruction = systemInstruction
        currentChannels = channels
        currentExtraContext = extraContext
        AppLogger.d(TAG, ">>> initialize() CALLED <<<")
        AppLogger.d(TAG, "modelPath: $modelPath")
        AppLogger.d(TAG, "params: maxTokens=${params.maxNumTokens}, temp=${params.temperature}, topK=${params.topK}, topP=${params.topP}")
        AppLogger.d(TAG, "tools: ${tools.size} ToolProviders")

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
            AppLogger.w(TAG, "GPU failed: ${e.message}")
            if (e.message?.contains("PERMISSION_DENIED") == true || e.message?.contains("open() failed") == true) {
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
        AppLogger.d(TAG, "Backend: $backend")

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
        }

        AppLogger.d(TAG, "EngineConfig created with path: $actualPath")
        AppLogger.d(TAG, "Creating Engine instance...")
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
        AppLogger.d(TAG, "[ULTRA] SamplerConfig: topK=${currentParams.topK}, topP=${currentParams.topP}, temp=${currentParams.temperature}")
        AppLogger.d(TAG, "[ULTRA] currentTools count: ${currentTools.size}")

        // Create ConversationConfig — automaticToolCalling=DISABLED for manual tool calling
        // Our callback handles tool calls instead of the broken Conversation internal loop
        val convConfig = ConversationConfig(
            samplerConfig = samplerConfig,
            tools = currentTools,
            systemInstruction = currentSystemInstruction,
            channels = currentChannels,
            extraContext = currentExtraContext ?: emptyMap(),
            automaticToolCalling = false  // Manual mode: let our callback handle tool calls
        )

        conversation = engine!!.createConversation(convConfig)
        AppLogger.d(TAG, "Conversation created: $conversation")
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
val convConfig = ConversationConfig(
            samplerConfig = samplerConfig,
            tools = currentTools,
            systemInstruction = currentSystemInstruction,
            channels = currentChannels,
            extraContext = currentExtraContext ?: emptyMap(),
            automaticToolCalling = false  // Manual mode
        )
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
        onError: (Throwable) -> Unit,
        extraContext: Map<String, String> = emptyMap(),
        onThinking: ((String) -> Unit)? = null  // THINK-06: nullable callback for thought channel tokens
    ) {
        AppLogger.d(TAG, ">>> sendMessage() CALLED <<<")

        if (conversation == null) {
            AppLogger.e(TAG, "conversation is NULL!")
            onError(IllegalStateException("Conversation not initialized"))
            return
        }

        // WORKAROUND: ConversationConfig.systemInstruction is ignored by Gemma-4-E2B-IT.
        // Prepend system instruction to every user message so the model actually sees it.
        val sysInstrText = currentSystemInstruction?.toString() ?: ""
        val fullMessage = if (sysInstrText.isNotEmpty()) {
            "$sysInstrText\n\nUser: $message"
        } else {
            message
        }
        AppLogger.d(TAG, "message: $message")
        AppLogger.d(TAG, "fullMessage (with sysInstr): ${fullMessage.take(100)}...")

        val messageContents = Contents.of(fullMessage)
        AppLogger.d(TAG, "[ULTRA] Contents.of(string) created: '${fullMessage.take(50)}...'")

        val callback = object : MessageCallback {
            private var done = false

            override fun onMessage(message: Message) {
                AppLogger.d(TAG, "[ULTRA] === onMessage() CALLED === role=${message.role} toolCalls=${message.toolCalls.size}")
                AppLogger.d(TAG, "[ULTRA] message.toString() length: ${message.toString().length}")
                AppLogger.d(TAG, "[ULTRA] message.toString(): '${message.toString().take(200)}'")
                AppLogger.d(TAG, "[ULTRA] message.toolCalls.size: ${message.toolCalls.size}")
                AppLogger.d(TAG, "[ULTRA] message.channels.keys: ${message.channels.keys}")
                message.toolCalls.forEachIndexed { idx, tc ->
                    AppLogger.d(TAG, "[ULTRA]   toolCall[$idx]: name=${tc.name} args=${tc.arguments}")
                }
                AppLogger.d(TAG, "[TOOL-DEBUG] role=${message.role} toolCalls=${message.toolCalls.size} channels.keys=${message.channels.keys}")

                val msgStr = message.toString()
                if (msgStr.contains(".pdf") || msgStr.contains(".md") || msgStr.contains("KB")) {
                    AppLogger.i(TAG, "[TOOL-DEBUG] *** MODEL OUTPUT FILE CONTENT: '$msgStr' ***")
                }

                // MANUAL TOOL CALLING MODE: handle tool calls inline
                if (message.toolCalls.isNotEmpty()) {
                    AppLogger.d(TAG, "[TOOL] Detected ${message.toolCalls.size} tool call(s) in manual mode")
                    for (toolCall in message.toolCalls) {
                        AppLogger.d(TAG, "[TOOL] name=${toolCall.name} args=${toolCall.arguments}")
                        val toolResult = executeToolCall(toolCall.name, toolCall.arguments)
                        AppLogger.d(TAG, "[TOOL] result for ${toolCall.name}: $toolResult")
                        sendToolResult(toolCall.name, toolResult, onToken, onDone, onError)
                        return
                    }
                }

                val text = message.toString()
                AppLogger.d(TAG, "onMessage: '$text'")

                // Log thinking channel content if present
                val thinkingContent = message.channels["thought"]
                if (!thinkingContent.isNullOrEmpty()) {
                    AppLogger.d(TAG, "[THOUGHT-CHANNEL] $thinkingContent")
                    onThinking?.invoke(thinkingContent)
                }

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
        AppLogger.d(TAG, "[ULTRA] extraContext for this message: $extraContext")
        conversation?.sendMessageAsync(messageContents, callback, extraContext)
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
        val fileSize = 56 + dataSize

        val wav = ByteArray(56 + dataSize)
        // RIFF header
        wav[0] = 'R'.code.toByte(); wav[1] = 'I'.code.toByte(); wav[2] = 'F'.code.toByte(); wav[3] = 'F'.code.toByte()
        wav[4] = (fileSize and 0xFF).toByte(); wav[5] = ((fileSize shr 8) and 0xFF).toByte()
        wav[6] = ((fileSize shr 16) and 0xFF).toByte(); wav[7] = ((fileSize shr 24) and 0xFF).toByte()
        // WAVE
        wav[8] = 'W'.code.toByte(); wav[9] = 'A'.code.toByte(); wav[10] = 'V'.code.toByte(); wav[11] = 'E'.code.toByte()
        // fmt chunk
        wav[12] = 'f'.code.toByte(); wav[13] = 'm'.code.toByte(); wav[14] = 't'.code.toByte(); wav[15] = ' '.code.toByte()
        wav[16] = 16; wav[17] = 0; wav[18] = 0; wav[19] = 0
        wav[20] = 1; wav[21] = 0
        wav[22] = numChannels.toByte(); wav[23] = 0
        wav[24] = (sampleRate and 0xFF).toByte(); wav[25] = ((sampleRate shr 8) and 0xFF).toByte()
        wav[26] = ((sampleRate shr 16) and 0xFF).toByte(); wav[27] = ((sampleRate shr 24) and 0xFF).toByte()
        wav[28] = (byteRate and 0xFF).toByte(); wav[29] = ((byteRate shr 8) and 0xFF).toByte()
        wav[30] = ((byteRate shr 16) and 0xFF).toByte(); wav[31] = ((byteRate shr 24) and 0xFF).toByte()
        wav[32] = blockAlign.toByte(); wav[33] = 0
        wav[34] = bitsPerSample.toByte(); wav[35] = 0
        // fact chunk
        wav[36] = 'f'.code.toByte(); wav[37] = 'a'.code.toByte(); wav[38] = 'c'.code.toByte(); wav[39] = 't'.code.toByte()
        wav[40] = 4; wav[41] = 0; wav[42] = 0; wav[43] = 0
        wav[44] = (numSamples and 0xFF).toByte(); wav[45] = ((numSamples shr 8) and 0xFF).toByte()
        wav[46] = ((numSamples shr 16) and 0xFF).toByte(); wav[47] = ((numSamples shr 24) and 0xFF).toByte()
        // data chunk
        wav[48] = 'd'.code.toByte(); wav[49] = 'a'.code.toByte(); wav[50] = 't'.code.toByte(); wav[51] = 'a'.code.toByte()
        wav[52] = (dataSize and 0xFF).toByte(); wav[53] = ((dataSize shr 8) and 0xFF).toByte()
        wav[54] = ((dataSize shr 16) and 0xFF).toByte(); wav[55] = ((dataSize shr 24) and 0xFF).toByte()
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

        AppLogger.d(TAG, "Sending audio as WAV ${wrapAudioInWav(audioBytes).size} bytes")
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

    fun reload(params: LlmParams, systemInstruction: Contents? = null, onProgress: (String, Int) -> Unit = { _, _ -> }) {
        AppLogger.d(TAG, ">>> reload() CALLED <<<")
        val ctx = currentContext ?: throw IllegalStateException("No context set - call initialize first")
        val path = currentModelPath
        val tools = currentTools
        val agentTools = currentAgentTools
        val sysInstr = systemInstruction ?: currentSystemInstruction
        val ch = currentChannels
        val extra = currentExtraContext
        release()
        currentParams = params
        initialize(ctx, path, params, null, tools, sysInstr, ch, extra, onProgress)
    }

    private var agentToolsInstance: AgentTools? = null

    fun registerAgentTools(tools: List<ToolProvider>) {
        AppLogger.d(TAG, "[TOOL] registerAgentTools() stub — AgentTools passed directly")
    }

    private fun executeToolCall(name: String, arguments: Map<String, Any?>): Map<String, Any?> {
        AppLogger.d(TAG, "[TOOL] executeToolCall: $name args=$arguments")
        val result = callAgentToolsMethod(name, arguments)
        return result ?: mapOf("result" to "error", "message" to "Tool $name not found on AgentTools")
    }

    @Suppress("UNCHECKED_CAST")
    private val agentToolsMethods: Map<String, java.lang.reflect.Method> by lazy {
        agentToolsInstance?.let { inst ->
            inst::class.java.methods
                .filter { it.declaringClass == AgentTools::class.java }
                .associate { it.name to it } as Map<String, java.lang.reflect.Method>
        } ?: emptyMap()
    }

    private fun callAgentToolsMethod(name: String, args: Map<String, Any?>): Map<String, Any?>? {
        try {
            val instance = agentToolsInstance ?: return null
            AppLogger.d(TAG, "[TOOL] agentToolsInstance is ${if (instance != null) "SET ($instance)" else "NULL"}")
            // Model sends snake_case (list_workspace) but Kotlin methods are camelCase (listWorkspace)
            val camelName = name.split("_").mapIndexed { i, part ->
                if (i == 0) part else part.replaceFirstChar { it.uppercase() }
            }.joinToString("")
            AppLogger.d(TAG, "[TOOL] Looking for method '$name' (converted to '$camelName') in ${agentToolsMethods.size} AgentTools methods")
            val method = agentToolsMethods[camelName] ?: run {
                AppLogger.w(TAG, "[TOOL] Method '$name' not found in AgentTools")
                return null
            }
            AppLogger.d(TAG, "[TOOL] Calling AgentTools.$name via reflection")
            val result = method.invoke(instance)
            @Suppress("UNCHECKED_CAST")
            return result as? Map<String, Any?>
        } catch (e: Exception) {
            AppLogger.e(TAG, "[TOOL] callAgentToolsMethod error for $name: ${e.message}", e)
            return null
        }
    }

    private fun sendToolResult(
        toolName: String,
        result: Map<String, Any?>,
        onToken: (String) -> Unit,
        onDone: () -> Unit,
        onError: (Throwable) -> Unit
    ) {
        try {
            val resultJson = mapToJson(result)
            AppLogger.d(TAG, "[TOOL] sendToolResult json=$resultJson")
            val content = Content.ToolResponse(toolName, resultJson)
            val toolMessage = Message.tool(Contents.of(content))
            conversation?.sendMessageAsync(toolMessage, object : MessageCallback {
                private var done = false
                override fun onMessage(message: Message) {
                    val text = message.toString()
                    if (text.isNotEmpty()) {
                        AppLogger.d(TAG, "[TOOL-RESPONSE] onMessage: '$text'")
                        try { onToken(text) } catch (e: Exception) { AppLogger.e(TAG, "onToken threw", e) }
                    }
                }
                override fun onDone() {
                    if (done) return
                    done = true
                    AppLogger.i(TAG, "[TOOL-RESPONSE] onDone")
                    try { onDone() } catch (e: Exception) { AppLogger.e(TAG, "onDone threw", e) }
                }
                override fun onError(throwable: Throwable) {
                    AppLogger.e(TAG, "[TOOL-RESPONSE] onError: ${throwable.message}", throwable)
                    try { onError(throwable) } catch (e: Exception) { AppLogger.e(TAG, "onError threw", e) }
                }
            })
            AppLogger.d(TAG, "[TOOL] Sent tool result for $toolName back to model")
        } catch (e: Exception) {
            AppLogger.e(TAG, "[TOOL] sendToolResult error: ${e.message}", e)
        }
    }

    private fun mapToJson(map: Map<String, Any?>): String {
        val sb = StringBuilder("{")
        map.entries.forEachIndexed { idx, (key, value) ->
            if (idx > 0) sb.append(",")
            sb.append("\"$key\":")
            when (value) {
                is String -> sb.append("\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\"")
                is Number -> sb.append(value.toString())
                is Boolean -> sb.append(value.toString())
                is List<*> -> sb.append(listToJson(value as List<Any?>))
                is Map<*, *> -> sb.append(mapToJson(value as Map<String, Any?>))
                null -> sb.append("null")
                else -> sb.append("\"${value.toString().replace("\\", "\\\\").replace("\"", "\\\"")}\"")
            }
        }
        sb.append("}")
        return sb.toString()
    }

    private fun listToJson(list: List<Any?>): String {
        val sb = StringBuilder("[")
        list.forEachIndexed { idx, value ->
            if (idx > 0) sb.append(",")
            when (value) {
                is String -> sb.append("\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\"")
                is Number -> sb.append(value.toString())
                is Boolean -> sb.append(value.toString())
                is List<*> -> sb.append(listToJson(value as List<Any?>))
                is Map<*, *> -> sb.append(mapToJson(value as Map<String, Any?>))
                null -> sb.append("null")
                else -> sb.append("\"${value.toString().replace("\\", "\\\\").replace("\"", "\\\"")}\"")
            }
        }
        sb.append("]")
        return sb.toString()
    }
}
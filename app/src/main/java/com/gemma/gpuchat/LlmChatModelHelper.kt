package com.gemma.gpuchat

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback

object LlmChatModelHelper {
    private var engine: Engine? = null
    private var conversation: Conversation? = null
    private val TAG = "LlmChatModelHelper"

    fun initialize(context: Context, modelPath: String) {
        Log.d(TAG, "initialize called with path: $modelPath")
        Log.d(TAG, "filesDir: ${context.filesDir}")
        try {
            val engineConfig = EngineConfig(
                modelPath = modelPath,
                backend = Backend.GPU(),
                maxNumTokens = 2048
            )
            Log.d(TAG, "Creating Engine with GPU backend...")
            engine = Engine(engineConfig).apply {
                Log.d(TAG, "Calling engine.initialize()...")
                initialize()
                Log.d(TAG, "engine.initialize() returned!")
            }
            Log.d(TAG, "Engine created successfully!")
            conversation = engine!!.createConversation()
            Log.d(TAG, "Conversation created!")
        } catch (e: Exception) {
            Log.e(TAG, "Initialization failed", e)
            throw e
        }
    }

    fun sendMessage(
        message: String,
        onToken: (String) -> Unit,
        onDone: () -> Unit,
        onError: (Throwable) -> Unit
    ) {
        Log.d(TAG, "sendMessage called: $message")
        val callback = object : MessageCallback {
            override fun onMessage(message: Message) {
                Log.d(TAG, "onMessage: $message")
                onToken(message.toString())
            }
            override fun onDone() {
                Log.d(TAG, "onDone")
                onDone()
            }
            override fun onError(throwable: Throwable) {
                Log.e(TAG, "onError", throwable)
                onError(throwable)
            }
        }
        conversation?.sendMessageAsync(
            Contents.of(message),
            callback
        )
    }

    fun release() {
        Log.d(TAG, "release called")
        conversation?.close()
        engine?.close()
        conversation = null
        engine = null
    }
}

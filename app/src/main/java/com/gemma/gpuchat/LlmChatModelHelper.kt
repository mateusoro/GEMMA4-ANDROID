package com.gemma.gpuchat

import android.content.Context
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

    fun initialize(context: Context, modelPath: String) {
        val engineConfig = EngineConfig(
            modelPath = modelPath,
            backend = Backend.GPU(),
            maxNumTokens = 2048
        )
        engine = Engine(engineConfig).apply { initialize() }
        conversation = engine!!.createConversation()
    }

    fun sendMessage(
        message: String,
        onToken: (String) -> Unit,
        onDone: () -> Unit,
        onError: (Throwable) -> Unit
    ) {
        val callback = object : MessageCallback {
            override fun onMessage(message: Message) { onToken(message.toString()) }
            override fun onDone() { onDone() }
            override fun onError(throwable: Throwable) { onError(throwable) }
        }
        conversation?.sendMessageAsync(
            Contents.of(message),
            callback
        )
    }

    fun release() {
        conversation?.close()
        engine?.close()
        conversation = null
        engine = null
    }
}

package com.gemma.gpuchat

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import java.io.File

/**
 * Transcriber dedicated to audio-only transcription using Gemma 4 multimodal.
 * Uses its own Engine and Conversation — completely isolated from the chat LLM.
 * This way the chat history is never polluted by audio transcription turns.
 */
class AudioTranscriber(private val context: Context) {

    private var engine: Engine? = null
    private var conversation: Conversation? = null

    private val modelPath = "/data/local/tmp/gemma-4-E2B-it.litertlm"

    fun initialize(onProgress: (String, Int) -> Unit = { _, _ -> }) {
        onProgress("Iniciando transcriber...", 0)

        val modelFile = File(modelPath)
        if (!modelFile.exists()) {
            throw IllegalStateException("Model not found at $modelPath")
        }

        // Use CPU for audio — GPU doesn't support audio decoding (Issue #1575)
        val audioBackend = Backend.CPU()

        val engineConfig = EngineConfig(
            modelPath = modelPath,
            backend = Backend.GPU(),
            audioBackend = audioBackend,
            maxNumImages = 4
        )

        engine = Engine(engineConfig)
        onProgress("Engine do transcriber criado...", 60)

        engine!!.initialize()
        onProgress("Engine do transcriber inicializado...", 80)

        val samplerConfig = SamplerConfig(topK = 1, topP = 0.0, temperature = 0.0)
        val convConfig = ConversationConfig(samplerConfig = samplerConfig)
        conversation = engine!!.createConversation(convConfig)

        onProgress("Transcriber pronto!", 100)
        AppLogger.i(TAG, ">>> AudioTranscriber INITIALIZED <<<")
    }

    fun isReady(): Boolean = conversation != null && engine != null

    /**
     * Transcribe raw PCM 16kHz mono audio bytes.
     * Returns the transcription string.
     */
    fun transcribe(
        audioBytes: ByteArray,
        onToken: (String) -> Unit,
        onDone: () -> Unit,
        onError: (Throwable) -> Unit
    ) {
        AppLogger.d(TAG, ">>> transcribe() CALLED audioBytes=${audioBytes.size}")
        if (conversation == null) {
            AppLogger.e(TAG, "transcriber conversation is NULL - not initialized")
            onError(IllegalStateException("AudioTranscriber not initialized"))
            return
        }

        val wavAudio = wrapAudioInWav(audioBytes)
        AppLogger.d(TAG, "WAV wrapped: ${wavAudio.size} bytes")

        val callback = object : MessageCallback {
            private var done = false

            override fun onMessage(message: Message) {
                if (done) return
                val text = message.toString()
                AppLogger.d(TAG, "transcribe onMessage: '$text'")
                try { onToken(text) } catch (e: Exception) { AppLogger.e(TAG, "onToken threw", e) }
            }

            override fun onDone() {
                if (done) return
                done = true
                AppLogger.i(TAG, "transcribe onDone")
                try { onDone() } catch (e: Exception) { AppLogger.e(TAG, "onDone threw", e) }
            }

            override fun onError(throwable: Throwable) {
                AppLogger.e(TAG, "transcribe onError: ${throwable.message}", throwable)
                try { onError(throwable) } catch (e: Exception) { AppLogger.e(TAG, "onError threw", e) }
            }
        }

        conversation?.sendMessageAsync(
            Contents.of(
                Content.AudioBytes(wavAudio),
                Content.Text("Transcribe the following speech segment in its original language. Only output the transcription.")
            ),
            callback
        )
    }

    /**
     * Release all resources. Call when done with transcription.
     */
    fun release() {
        AppLogger.d(TAG, ">>> AudioTranscriber release() <<<")
        conversation?.close()
        engine?.close()
        conversation = null
        engine = null
        AppLogger.i(TAG, "AudioTranscriber released")
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
        wav[0] = 'R'.code.toByte(); wav[1] = 'I'.code.toByte(); wav[2] = 'F'.code.toByte(); wav[3] = 'F'.code.toByte()
        wav[4] = (fileSize and 0xFF).toByte(); wav[5] = ((fileSize shr 8) and 0xFF).toByte()
        wav[6] = ((fileSize shr 16) and 0xFF).toByte(); wav[7] = ((fileSize shr 24) and 0xFF).toByte()
        wav[8] = 'W'.code.toByte(); wav[9] = 'A'.code.toByte(); wav[10] = 'V'.code.toByte(); wav[11] = 'E'.code.toByte()
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
        wav[36] = 'f'.code.toByte(); wav[37] = 'a'.code.toByte(); wav[38] = 'c'.code.toByte(); wav[39] = 't'.code.toByte()
        wav[40] = 4; wav[41] = 0; wav[42] = 0; wav[43] = 0
        wav[44] = (numSamples and 0xFF).toByte(); wav[45] = ((numSamples shr 8) and 0xFF).toByte()
        wav[46] = ((numSamples shr 16) and 0xFF).toByte(); wav[47] = ((numSamples shr 24) and 0xFF).toByte()
        wav[48] = 'd'.code.toByte(); wav[49] = 'a'.code.toByte(); wav[50] = 't'.code.toByte(); wav[51] = 'a'.code.toByte()
        wav[52] = (dataSize and 0xFF).toByte(); wav[53] = ((dataSize shr 8) and 0xFF).toByte()
        wav[54] = ((dataSize shr 16) and 0xFF).toByte(); wav[55] = ((dataSize shr 24) and 0xFF).toByte()
        for (i in audioBytes.indices) {
            wav[56 + i] = audioBytes[i]
        }
        return wav
    }

    companion object {
        private const val TAG = "AudioTranscriber"
    }
}
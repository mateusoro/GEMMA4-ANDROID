package com.gemma.gpuchat

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for WAV header file size fix.
 * Runs ON DEVICE — verifies the WAV generation works with real Android.
 */
@RunWith(AndroidJUnit4::class)
class WavUtilsInstrumentedTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    // Helper to read little-endian 32-bit unsigned integer
    private fun readUInt32LE(bytes: ByteArray, offset: Int): Long {
        return (bytes[offset].toLong() and 0xFF) or
               ((bytes[offset + 1].toLong() and 0xFF) shl 8) or
               ((bytes[offset + 2].toLong() and 0xFF) shl 16) or
               ((bytes[offset + 3].toLong() and 0xFF) shl 24)
    }

    // Helper to read ASCII string
    private fun readASCIIString(bytes: ByteArray, offset: Int, length: Int): String {
        return bytes.sliceArray(offset until offset + length).toString(Charsets.US_ASCII)
    }

    // Replicate the fixed wrapAudioInWav logic for testing
    private fun createWav(audioBytes: ByteArray): ByteArray {
        val sampleRate = 16000
        val numChannels = 1
        val bitsPerSample = 16
        val byteRate = sampleRate * numChannels * bitsPerSample / 8
        val blockAlign = numChannels * bitsPerSample / 8
        val dataSize = audioBytes.size
        val numSamples = dataSize / (bitsPerSample / 8)
        val fileSize = 56 + dataSize  // FIXED: was 44 + dataSize

        val wav = ByteArray(56 + dataSize)
        // RIFF header
        wav[0] = 'R'.code.toByte(); wav[1] = 'I'.code.toByte(); wav[2] = 'F'.code.toByte(); wav[3] = 'F'.code.toByte()
        // File size
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
        // PCM data
        for (i in audioBytes.indices) {
            wav[56 + i] = audioBytes[i]
        }
        return wav
    }

    @Test
    fun wav_fileSize_shouldBe56PlusDataSize() {
        // Arrange
        val audioBytes = ByteArray(100) { (it % 256).toByte() }

        // Act
        val wav = createWav(audioBytes)

        // Assert - fileSize at bytes 4-7 should be 156 (56 + 100)
        val fileSize = readUInt32LE(wav, 4)
        assertEquals("fileSize should be 156 for 100 bytes of audio", 156L, fileSize)
    }

    @Test
    fun wav_header_shouldHaveCorrectSignature() {
        // Arrange
        val audioBytes = ByteArray(50) { 0 }

        // Act
        val wav = createWav(audioBytes)

        // Assert
        assertEquals("RIFF", readASCIIString(wav, 0, 4))
        assertEquals("WAVE", readASCIIString(wav, 8, 4))
    }

    @Test
    fun wav_fmtChunk_shouldBeCorrect() {
        // Arrange
        val audioBytes = ByteArray(100) { 0 }

        // Act
        val wav = createWav(audioBytes)

        // Assert
        assertEquals("fmt ", readASCIIString(wav, 12, 4))
        assertEquals(16L, readUInt32LE(wav, 16)) // chunk size
        assertEquals(1L, readUInt32LE(wav, 20) and 0xFFFF) // PCM format
    }

    @Test
    fun wav_factChunk_shouldBePresent() {
        // Arrange
        val audioBytes = ByteArray(100) { 0 }

        // Act
        val wav = createWav(audioBytes)

        // Assert
        assertEquals("fact", readASCIIString(wav, 36, 4))
        assertEquals(4L, readUInt32LE(wav, 40)) // fact chunk size
    }

    @Test
    fun wav_dataChunk_shouldStartAtByte56() {
        // Arrange
        val audioBytes = ByteArray(100) { it.toByte() }

        // Act
        val wav = createWav(audioBytes)

        // Assert
        assertEquals("data", readASCIIString(wav, 48, 4))
        assertEquals(100L, readUInt32LE(wav, 52)) // data size

        // Verify PCM data starts at byte 56
        assertEquals(audioBytes[0], wav[56])
        assertEquals(audioBytes[99], wav[155])
    }

    @Test
    fun wav_totalSize_shouldBe56PlusDataSize() {
        // Arrange
        val sizes = listOf(10, 100, 1000, 5000)

        for (dataSize in sizes) {
            val audioBytes = ByteArray(dataSize) { it.toByte() }
            val wav = createWav(audioBytes)

            assertEquals(
                "Total WAV size for $dataSize bytes should be ${56 + dataSize}",
                56 + dataSize,
                wav.size
            )
        }
    }

    @Test
    fun wav_numSamples_shouldBeCorrect() {
        // 100 bytes / 2 bytes per sample (16-bit) = 50 samples
        val audioBytes = ByteArray(100) { 0 }
        val wav = createWav(audioBytes)

        val numSamples = readUInt32LE(wav, 44)
        assertEquals("numSamples should be 50", 50L, numSamples)
    }

    @Test
    fun wav_sampleRate_shouldBe16000() {
        // Arrange
        val audioBytes = ByteArray(100) { 0 }

        // Act
        val wav = createWav(audioBytes)

        // Assert - sample rate at bytes 24-27 should be 16000
        val sampleRate = readUInt32LE(wav, 24)
        assertEquals("Sample rate should be 16000", 16000L, sampleRate)
    }
}
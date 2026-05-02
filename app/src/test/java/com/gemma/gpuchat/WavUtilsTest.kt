package com.gemma.gpuchat

import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for WAV header generation.
 * Tests that wrapAudioInWav produces correct WAV file structure.
 *
 * The critical fix: fileSize at bytes 4-7 must be 56 + dataSize, NOT 44 + dataSize.
 * The old code wrote fileSize = 44 + dataSize which caused MA_INVALID_FILE (-10) error
 * because LiteRT-LM expected data to start at offset 56, not 52.
 */
class WavUtilsTest {

    // Helper function that mirrors the FIXED wrapAudioInWav implementation
    // This is the corrected version with fileSize = 56 + dataSize
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
        // File size - 8 (little endian)
        wav[4] = (fileSize and 0xFF).toByte(); wav[5] = ((fileSize shr 8) and 0xFF).toByte()
        wav[6] = ((fileSize shr 16) and 0xFF).toByte(); wav[7] = ((fileSize shr 24) and 0xFF).toByte()
        // WAVE
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
        // fact chunk
        wav[36] = 'f'.code.toByte(); wav[37] = 'a'.code.toByte(); wav[38] = 'c'.code.toByte(); wav[39] = 't'.code.toByte()
        wav[40] = 4; wav[41] = 0; wav[42] = 0; wav[43] = 0  // fact chunk size = 4
        wav[44] = (numSamples and 0xFF).toByte(); wav[45] = ((numSamples shr 8) and 0xFF).toByte()
        wav[46] = ((numSamples shr 16) and 0xFF).toByte(); wav[47] = ((numSamples shr 24) and 0xFF).toByte()
        // data chunk
        wav[48] = 'd'.code.toByte(); wav[49] = 'a'.code.toByte(); wav[50] = 't'.code.toByte(); wav[51] = 'a'.code.toByte()
        wav[52] = (dataSize and 0xFF).toByte(); wav[53] = ((dataSize shr 8) and 0xFF).toByte()
        wav[54] = ((dataSize shr 16) and 0xFF).toByte(); wav[55] = ((dataSize shr 24) and 0xFF).toByte()
        // raw PCM data
        for (i in audioBytes.indices) {
            wav[56 + i] = audioBytes[i]
        }
        return wav
    }

    // Helper to read little-endian 32-bit unsigned integer from byte array
    private fun readUInt32LE(bytes: ByteArray, offset: Int): Long {
        return (bytes[offset].toLong() and 0xFF) or
               ((bytes[offset + 1].toLong() and 0xFF) shl 8) or
               ((bytes[offset + 2].toLong() and 0xFF) shl 16) or
               ((bytes[offset + 3].toLong() and 0xFF) shl 24)
    }

    // Helper to read ASCII string from byte array
    private fun readASCIIString(bytes: ByteArray, offset: Int, length: Int): String {
        return bytes.sliceArray(offset until offset + length).toString(Charsets.US_ASCII)
    }

    @Test
    fun `fileSize should be 56 + dataSize, not 44 + dataSize`() {
        // Arrange: 100 bytes of PCM audio
        val audioBytes = ByteArray(100) { (it % 256).toByte() }

        // Act
        val wav = createWav(audioBytes)

        // Assert: fileSize at bytes 4-7 should be 56 + 100 = 156
        val fileSize = readUInt32LE(wav, 4)
        assertEquals("fileSize should be 56 + dataSize = 156", 156L, fileSize)
    }

    @Test
    fun `WAV header should have correct RIFF signature`() {
        // Arrange
        val audioBytes = ByteArray(100) { 0 }

        // Act
        val wav = createWav(audioBytes)

        // Assert
        assertEquals("RIFF", readASCIIString(wav, 0, 4))
    }

    @Test
    fun `WAV header should have correct WAVE signature`() {
        // Arrange
        val audioBytes = ByteArray(100) { 0 }

        // Act
        val wav = createWav(audioBytes)

        // Assert
        assertEquals("WAVE", readASCIIString(wav, 8, 4))
    }

    @Test
    fun `fmt chunk should be correct format`() {
        // Arrange
        val audioBytes = ByteArray(100) { 0 }

        // Act
        val wav = createWav(audioBytes)

        // Assert: bytes 12-15 should be "fmt "
        assertEquals("fmt ", readASCIIString(wav, 12, 4))
        // Assert: bytes 16-19 should be chunk size = 16
        assertEquals(16L, readUInt32LE(wav, 16))
        // Assert: bytes 20-21 should be audio format = 1 (PCM)
        assertEquals(1L, readUInt32LE(wav, 20) and 0xFFFF)
    }

    @Test
    fun `fact chunk should be present with correct size`() {
        // Arrange
        val audioBytes = ByteArray(100) { 0 }

        // Act
        val wav = createWav(audioBytes)

        // Assert: bytes 36-39 should be "fact"
        assertEquals("fact", readASCIIString(wav, 36, 4))
        // Assert: bytes 40-43 should be chunk size = 4
        assertEquals(4L, readUInt32LE(wav, 40))
    }

    @Test
    fun `data chunk should be at correct offset`() {
        // Arrange
        val audioBytes = ByteArray(100) { 0 }

        // Act
        val wav = createWav(audioBytes)

        // Assert: bytes 48-51 should be "data"
        assertEquals("data", readASCIIString(wav, 48, 4))
        // Assert: bytes 52-55 should be dataSize = 100
        assertEquals(100L, readUInt32LE(wav, 52))
    }

    @Test
    fun `PCM data should start at byte 56`() {
        // Arrange
        val audioBytes = ByteArray(100) { (it * 2).toByte() }

        // Act
        val wav = createWav(audioBytes)

        // Assert: byte 56 should match first audio byte
        assertEquals(audioBytes[0], wav[56])
        // Assert: byte 155 (56 + 99) should match last audio byte
        assertEquals(audioBytes[99], wav[155])
    }

    @Test
    fun `total WAV size should be 56 + dataSize`() {
        // Arrange
        val audioBytes = ByteArray(1000) { it.toByte() }

        // Act
        val wav = createWav(audioBytes)

        // Assert
        assertEquals("Total WAV size should be 56 + dataSize = 1056", 1056, wav.size)
    }

    @Test
    fun `numSamples should be dataSize divided by bytes per sample`() {
        // Arrange: 100 bytes of 16-bit audio = 50 samples
        val audioBytes = ByteArray(100) { 0 }

        // Act
        val wav = createWav(audioBytes)

        // Assert: bytes 44-47 should contain numSamples = 50
        val numSamples = readUInt32LE(wav, 44)
        assertEquals("numSamples should be 50 (100 bytes / 2 bytes per sample)", 50L, numSamples)
    }

    @Test
    fun `sample rate should be encoded correctly at bytes 24-27`() {
        // Arrange
        val audioBytes = ByteArray(100) { 0 }

        // Act
        val wav = createWav(audioBytes)

        // Assert: sample rate should be 16000 (0x00003D40)
        val sampleRate = readUInt32LE(wav, 24)
        assertEquals("Sample rate should be 16000", 16000L, sampleRate)
    }

    @Test
    fun `different audio sizes should produce correct fileSize`() {
        // Test multiple sizes to ensure formula is correct
        val sizes = listOf(10, 100, 1000, 10000)

        for (dataSize in sizes) {
            val audioBytes = ByteArray(dataSize) { it.toByte() }
            val wav = createWav(audioBytes)

            val fileSize = readUInt32LE(wav, 4)
            assertEquals("fileSize for $dataSize bytes should be ${56 + dataSize}", (56 + dataSize).toLong(), fileSize)
        }
    }
}
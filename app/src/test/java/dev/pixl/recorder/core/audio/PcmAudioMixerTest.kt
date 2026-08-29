package dev.pixl.recorder.core.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class PcmAudioMixerTest {

    @Test
    fun testMixStereo16BitWithSilence() {
        val gamePcm = ByteArray(8) // 2 stereo samples (4 bytes each) of silence
        val micPcm = ByteArray(8)
        val dest = ByteArray(8)

        val written = PcmAudioMixer.mixStereo16Bit(
            gamePcm, 8,
            micPcm, 8,
            1.0f, 1.0f,
            dest
        )

        assertEquals(8, written)
        for (b in dest) {
            assertEquals(0.toByte(), b)
        }
    }

    @Test
    fun testMixStereo16BitKnownValues() {
        // Create 1 sample of value 1000 in game and 2000 in mic
        val gamePcm = byteArrayOf(0xE8.toByte(), 0x03.toByte(), 0xE8.toByte(), 0x03.toByte()) // 1000 in both channels
        val micPcm = byteArrayOf(0xD0.toByte(), 0x07.toByte(), 0xD0.toByte(), 0x07.toByte()) // 2000 in both channels
        val dest = ByteArray(4)

        val written = PcmAudioMixer.mixStereo16Bit(
            gamePcm, 4,
            micPcm, 4,
            1.0f, 1.0f,
            dest
        )

        assertEquals(4, written)
        val leftLow = dest[0].toInt() and 0xFF
        val leftHigh = dest[1].toInt()
        val mixedSample = ((leftHigh shl 8) or leftLow).toShort()

        // 1000 + 2000 = 3000 (well within linear threshold < 26213)
        assertEquals(3000.toShort(), mixedSample)
    }

    @Test
    fun testSoftKneeLimiterPreventsClippingOverflow() {
        // Test with massive samples that would normally wrap around in 16-bit integer math (e.g. 30,000 + 30,000 = 60,000)
        val gameSample = 30000.0f
        val micSample = 30000.0f
        val sum = gameSample + micSample // 60,000

        val limited = PcmAudioMixer.softLimitSample(sum)
        val finalShort = limited.toInt().coerceIn(-32768, 32767).toShort()

        // Verify result is positive, clamped below 32767, and does not wrap into negative values
        assertTrue("Limited value should be <= 32767", finalShort <= 32767)
        assertTrue("Limited value should be > 26000", finalShort > 26000)
    }

    @Test
    fun testNegativeSoftKneeLimiter() {
        val sum = -60000.0f
        val limited = PcmAudioMixer.softLimitSample(sum)
        val finalShort = limited.toInt().coerceIn(-32768, 32767).toShort()

        assertTrue("Limited value should be >= -32768", finalShort >= -32768)
        assertTrue("Limited value should be < -26000", finalShort < -26000)
    }

    @Test
    fun testCalculateDbLevelForSilence() {
        val silence = ByteArray(16)
        val db = PcmAudioMixer.calculateDbLevel(silence, 16)
        assertEquals(-60.0f, db, 0.01f)
    }

    @Test
    fun testCalculateDbLevelForMaxVolume() {
        // Full scale square wave (32767)
        val maxWave = ByteArray(16)
        for (i in 0 until 4) {
            maxWave[i * 4] = 0xFF.toByte()
            maxWave[i * 4 + 1] = 0x7F.toByte() // 32767
            maxWave[i * 4 + 2] = 0xFF.toByte()
            maxWave[i * 4 + 3] = 0x7F.toByte()
        }

        val db = PcmAudioMixer.calculateDbLevel(maxWave, 16)
        // Should be approximately 0.0 dB
        assertTrue("Full scale should be close to 0 dB, was $db", abs(db) < 0.5f)
    }
}

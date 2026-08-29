package dev.pixl.recorder.core.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * High-performance 16-bit Linear PCM stereo audio mixer with soft-knee limiting
 * and real-time RMS/dB calculation for UI telemetry visualizers.
 */
object PcmAudioMixer {

    private const val MAX_16_BIT = 32767.0f
    private const val MIN_16_BIT = -32768.0f
    private const val MIN_DB = -60.0f

    /**
     * Mixes two 16-bit PCM stereo byte buffers (game audio + mic audio) with gain multipliers
     * and soft-knee saturation clipping protection into a destination buffer.
     *
     * @return Output size in bytes.
     */
    fun mixStereo16Bit(
        gamePcm: ByteArray,
        gameBytesRead: Int,
        micPcm: ByteArray,
        micBytesRead: Int,
        gameGain: Float,
        micGain: Float,
        destBuffer: ByteArray
    ): Int {
        val sampleCount = max(gameBytesRead, micBytesRead) / 2
        val gameShorts = gameBytesRead / 2
        val micShorts = micBytesRead / 2

        var destByteIndex = 0

        for (i in 0 until sampleCount) {
            val gameSample = if (i < gameShorts) {
                // Read 16-bit little-endian sample
                val low = gamePcm[i * 2].toInt() and 0xFF
                val high = gamePcm[i * 2 + 1].toInt()
                ((high shl 8) or low).toShort().toFloat() * gameGain
            } else {
                0.0f
            }

            val micSample = if (i < micShorts) {
                val low = micPcm[i * 2].toInt() and 0xFF
                val high = micPcm[i * 2 + 1].toInt()
                ((high shl 8) or low).toShort().toFloat() * micGain
            } else {
                0.0f
            }

            // Sum and apply soft-knee limiter (tanh compression to prevent digital clipping)
            val mixed = gameSample + micSample
            val limited = softLimitSample(mixed)
            val finalShort = limited.toInt().coerceIn(-32768, 32767).toShort()

            // Write little-endian short to destination
            destBuffer[destByteIndex++] = (finalShort.toInt() and 0xFF).toByte()
            destBuffer[destByteIndex++] = ((finalShort.toInt() shr 8) and 0xFF).toByte()
        }

        return destByteIndex
    }

    /**
     * Applies gain scaling to a single PCM 16-bit stream without mixing.
     */
    fun applyGain16Bit(
        srcPcm: ByteArray,
        bytesRead: Int,
        gain: Float,
        destBuffer: ByteArray
    ): Int {
        if (gain == 1.0f) {
            System.arraycopy(srcPcm, 0, destBuffer, 0, bytesRead)
            return bytesRead
        }

        val sampleCount = bytesRead / 2
        var destByteIndex = 0

        for (i in 0 until sampleCount) {
            val low = srcPcm[i * 2].toInt() and 0xFF
            val high = srcPcm[i * 2 + 1].toInt()
            val sample = ((high shl 8) or low).toShort().toFloat() * gain

            val limited = softLimitSample(sample)
            val finalShort = limited.toInt().coerceIn(-32768, 32767).toShort()

            destBuffer[destByteIndex++] = (finalShort.toInt() and 0xFF).toByte()
            destBuffer[destByteIndex++] = ((finalShort.toInt() shr 8) and 0xFF).toByte()
        }

        return destByteIndex
    }

    /**
     * Computes RMS audio level in decibels (-60 dB to 0 dB) from 16-bit PCM bytes.
     */
    fun calculateDbLevel(pcmBytes: ByteArray, length: Int): Float {
        if (length <= 0) return MIN_DB

        val sampleCount = length / 2
        var sumSquares = 0.0

        for (i in 0 until sampleCount) {
            val low = pcmBytes[i * 2].toInt() and 0xFF
            val high = pcmBytes[i * 2 + 1].toInt()
            val sample = ((high shl 8) or low).toDouble()
            sumSquares += sample * sample
        }

        val rms = sqrt(sumSquares / sampleCount)
        if (rms <= 1.0) return MIN_DB

        val db = 20.0 * log10(rms / MAX_16_BIT.toDouble())
        return db.toFloat().coerceIn(MIN_DB, 0.0f)
    }

    /**
     * Soft-knee limiter: linearly passes signals under 0.8 threshold, applies tanh compression above.
     */
    fun softLimitSample(sample: Float): Float {
        val abs = kotlin.math.abs(sample)
        val threshold = MAX_16_BIT * 0.8f

        return if (abs <= threshold) {
            sample
        } else {
            val sign = if (sample > 0) 1.0f else -1.0f
            val excess = abs - threshold
            val maxExcess = MAX_16_BIT - threshold
            val compressed = threshold + maxExcess * tanh((excess / maxExcess).toDouble()).toFloat()
            sign * compressed
        }
    }
}

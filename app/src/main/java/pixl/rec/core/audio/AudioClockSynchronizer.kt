package pixl.rec.core.audio

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Nano-precision audio clock synchronizer and drift corrector.
 *
 * Synchronizes PCM audio sample duration with the master video/session clock baseline,
 * enforces strictly monotonic presentation timestamps (PTS) across pause/resume and reconnects,
 * compensates for physical hardware oscillator drift using bounded micro-slew (SLO <= 80ms),
 * and maintains explicit accounting for trimmed queue buffers.
 */
class AudioClockSynchronizer(
    val sampleRate: Int = 48_000,
    val channelCount: Int = 2,
    var sessionBaseTimeNs: Long = System.nanoTime(),
    var audioSyncOffsetUs: Long = 0L,               // Configurable A/V sync offset (+ delays audio, - advances audio)
    val driftCorrectionThresholdUs: Long = 40_000L, // 40ms threshold to activate slew
    val maxCorrectionPerChunkUs: Long = 500L,       // Max 500us adjustment per ~21ms chunk (~2.4%)
    val timeProvider: () -> Long = { System.nanoTime() }
) {
    private val bytesPerStereoFrame = channelCount * 2 // 16-bit PCM

    private val isPaused = AtomicBoolean(false)
    private var pauseStartTimeNs = 0L
    private var totalPausedDurationNs = 0L

    private var cumulativeSamplePtsUs = 0L
    private var lastEmittedPtsUs = -1L
    private var isFirstChunk = true

    val totalTrimmedChunks = AtomicLong(0L)
    val totalTrimmedBytes = AtomicLong(0L)
    private val lastMeasuredDriftUs = AtomicLong(0L)

    /**
     * Calibrates baseline with unified session start time and optional sync offset.
     */
    fun reset(baseTimeNs: Long = timeProvider(), syncOffsetUs: Long = audioSyncOffsetUs) {
        sessionBaseTimeNs = baseTimeNs
        audioSyncOffsetUs = syncOffsetUs
        isPaused.set(false)
        pauseStartTimeNs = 0L
        totalPausedDurationNs = 0L
        cumulativeSamplePtsUs = 0L
        lastEmittedPtsUs = -1L
        isFirstChunk = true
        totalTrimmedChunks.set(0L)
        totalTrimmedBytes.set(0L)
        lastMeasuredDriftUs.set(0L)
    }

    /**
     * Pauses the clock synchronizer and records pause start time.
     */
    fun pause(nowNs: Long = timeProvider()) {
        if (isPaused.compareAndSet(false, true)) {
            pauseStartTimeNs = nowNs
        }
    }

    /**
     * Resumes the clock synchronizer and adds elapsed pause duration to offset.
     */
    fun resume(nowNs: Long = timeProvider()) {
        if (isPaused.compareAndSet(true, false)) {
            val pausedDelta = (nowNs - pauseStartTimeNs).coerceAtLeast(0L)
            totalPausedDurationNs += pausedDelta
        }
    }

    /**
     * Explicitly records a dropped/trimmed audio buffer from the capture queue,
     * accounting for its sample duration to prevent silent clock skew.
     */
    fun onBufferTrimmed(trimmedBytes: Int) {
        totalTrimmedChunks.incrementAndGet()
        totalTrimmedBytes.addAndGet(trimmedBytes.toLong())

        val droppedFrames = trimmedBytes / bytesPerStereoFrame
        val droppedDurationUs = if (sampleRate > 0) {
            (droppedFrames.toLong() * 1_000_000L) / sampleRate
        } else {
            0L
        }
        cumulativeSamplePtsUs += droppedDurationUs
    }

    /**
     * Computes the calibrated, monotonic presentation timestamp for a PCM audio chunk.
     *
     * Applies bounded micro-slew when audio sample clock drifts from reference session time,
     * maintaining drift <= 80ms SLO without introducing audible clicks or discontinuities.
     */
    fun computeNextChunkPtsUs(chunkBytes: Int, nowNs: Long = timeProvider()): Long {
        val frames = chunkBytes / bytesPerStereoFrame
        val chunkDurationUs = if (sampleRate > 0) {
            (frames.toLong() * 1_000_000L) / sampleRate
        } else {
            0L
        }

        // Active session time elapsed (excluding paused periods)
        val sessionElapsedNs = (nowNs - sessionBaseTimeNs - totalPausedDurationNs).coerceAtLeast(0L)
        val sessionElapsedUs = sessionElapsedNs / 1000L

        if (isFirstChunk) {
            isFirstChunk = false
            // Initial alignment: anchor initial PTS to session elapsed time plus user audio sync offset
            cumulativeSamplePtsUs = (sessionElapsedUs + audioSyncOffsetUs).coerceAtLeast(0L)
            lastEmittedPtsUs = cumulativeSamplePtsUs
            return cumulativeSamplePtsUs
        }

        // Advance sample clock by chunk duration
        cumulativeSamplePtsUs += chunkDurationUs

        // Measure drift between sample clock and session clock (accounting for user sync offset)
        val driftUs = cumulativeSamplePtsUs - sessionElapsedUs - audioSyncOffsetUs
        lastMeasuredDriftUs.set(driftUs)

        // Bounded slew adjustment if drift exceeds threshold
        var slewAdjustmentUs = 0L
        if (abs(driftUs) > driftCorrectionThresholdUs) {
            if (driftUs < 0) {
                // Audio sample clock is lagging behind session clock -> speed up PTS
                slewAdjustmentUs = min(maxCorrectionPerChunkUs, -driftUs)
            } else {
                // Audio sample clock is leading ahead of session clock -> slow down PTS
                slewAdjustmentUs = -min(maxCorrectionPerChunkUs, driftUs)
            }
        }

        cumulativeSamplePtsUs += slewAdjustmentUs

        // Guarantee strict monotonic progression (at least +1us or fraction of chunk)
        val minNextPtsUs = if (lastEmittedPtsUs >= 0L) lastEmittedPtsUs + 1L else 0L
        val finalPtsUs = max(minNextPtsUs, cumulativeSamplePtsUs)

        lastEmittedPtsUs = finalPtsUs
        return finalPtsUs
    }

    /**
     * Current measured drift in milliseconds (positive = audio leads, negative = audio lags).
     */
    fun getAudioDriftMs(): Long = lastMeasuredDriftUs.get() / 1000L
}

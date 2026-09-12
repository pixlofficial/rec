package pixl.rec.core.replay

import android.media.MediaCodec
import android.media.MediaFormat
import android.util.Log
import java.nio.ByteBuffer
import java.util.ArrayDeque
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Thread-safe, volatile RAM circular ring buffer for Instant Replay capture.
 *
 * Key invariants:
 * 1. **GOP-Aware Eviction:** Old samples are only evicted when a newer keyframe exists and the
 *    retained span exceeds [maxDurationUs]. The video buffer head is ALWAYS a keyframe.
 * 2. **Audio/Video Sync:** Audio samples older than the oldest retained video keyframe are pruned,
 *    preserving cross-clock timestamp alignment.
 * 3. **0ms Retroactive Snapshot:** Taking a snapshot clones packet metadata and normalizes
 *    presentation timestamps to start cleanly from PTS_0 = 0 without blocking encoder threads.
 */
class ReplayRingBuffer(
    val durationSeconds: Int = 30
) {
    private val tag = "ReplayRingBuffer"
    val maxDurationUs: Long = durationSeconds.toLong() * 1_000_000L

    private val lock = ReentrantLock()
    private val samples = ArrayDeque<ReplaySample>()

    var videoFormat: MediaFormat? = null
        private set

    var audioFormat: MediaFormat? = null
        private set

    private var totalBytes: Long = 0L
    private var lastVideoPtsUs: Long = 0L

    fun setVideoFormat(format: MediaFormat) {
        lock.withLock {
            videoFormat = format
            Log.i(tag, "Replay buffer video format registered: ${format.getString(MediaFormat.KEY_MIME)} (${format.getInteger(MediaFormat.KEY_WIDTH)}x${format.getInteger(MediaFormat.KEY_HEIGHT)})")
        }
    }

    fun setAudioFormat(format: MediaFormat) {
        lock.withLock {
            audioFormat = format
            Log.i(tag, "Replay buffer audio format registered: ${format.getString(MediaFormat.KEY_MIME)}")
        }
    }

    /**
     * Appends an encoded sample slice into the circular RAM buffer and triggers GOP-aware eviction.
     */
    fun addSample(logicalTrack: Int, buffer: ByteBuffer, bufferInfo: MediaCodec.BufferInfo) {
        if (bufferInfo.size <= 0) return

        // Deep-copy the sample payload so the encoder's output buffer can be returned immediately
        val data = ByteArray(bufferInfo.size)
        val oldPos = buffer.position()
        buffer.position(bufferInfo.offset)
        buffer.get(data, 0, bufferInfo.size)
        buffer.position(oldPos)

        val sample = ReplaySample(
            trackIndex = logicalTrack,
            data = data,
            presentationTimeUs = bufferInfo.presentationTimeUs,
            flags = bufferInfo.flags
        )

        lock.withLock {
            samples.addLast(sample)
            totalBytes += sample.sizeBytes

            if (logicalTrack == 0) {
                lastVideoPtsUs = sample.presentationTimeUs
            }

            trimOldSamplesLocked()
        }
    }

    /**
     * Trims expired samples while strictly preserving Group of Pictures (GOP) boundaries.
     */
    private fun trimOldSamplesLocked() {
        if (samples.isEmpty()) return

        // 1. Find all video keyframe indices and their timestamps
        val keyframeTimestamps = ArrayList<Long>()
        for (sample in samples) {
            if (sample.isKeyframe) {
                keyframeTimestamps.add(sample.presentationTimeUs)
            }
        }

        // Need at least 2 keyframes to safely evict the older GOP
        if (keyframeTimestamps.size < 2) return

        val secondKeyframePtsUs = keyframeTimestamps[1]
        val currentSpanUs = lastVideoPtsUs - secondKeyframePtsUs

        // If even without the first GOP we still meet or exceed the requested duration,
        // we can safely evict everything before the second keyframe.
        if (currentSpanUs >= maxDurationUs) {
            val targetNewHeadPtsUs = secondKeyframePtsUs

            while (samples.isNotEmpty()) {
                val head = samples.peekFirst() ?: break
                // Stop when we reach the second keyframe (which now becomes the new head)
                if (head.trackIndex == 0 && head.presentationTimeUs >= targetNewHeadPtsUs) {
                    break
                }
                // Also retain audio from targetNewHeadPtsUs onwards
                if (head.trackIndex == 1 && head.presentationTimeUs >= targetNewHeadPtsUs) {
                    break
                }

                val removed = samples.removeFirst()
                totalBytes -= removed.sizeBytes
            }
        }
    }

    /**
     * Takes an immutable, thread-safe snapshot of the current buffer.
     * All presentation timestamps are normalized so the first keyframe begins at PTS = 0.
     */
    fun snapshot(): ReplaySnapshot? {
        lock.withLock {
            val vFormat = videoFormat ?: run {
                Log.w(tag, "Cannot snapshot replay buffer: VideoFormat is null")
                return null
            }

            if (samples.isEmpty()) {
                Log.w(tag, "Cannot snapshot replay buffer: Buffer is empty")
                return null
            }

            // Locate the first keyframe in the buffer
            var firstKeyframe: ReplaySample? = null
            for (sample in samples) {
                if (sample.isKeyframe) {
                    firstKeyframe = sample
                    break
                }
            }

            if (firstKeyframe == null) {
                Log.w(tag, "Cannot snapshot replay buffer: No keyframe found in buffer")
                return null
            }

            val basePtsUs = firstKeyframe.presentationTimeUs
            var maxPtsUs = basePtsUs
            var snapshotBytes = 0L

            val normalizedSamples = ArrayList<ReplaySample>(samples.size)

            for (sample in samples) {
                // Drop any lingering packets older than the first keyframe
                if (sample.presentationTimeUs < basePtsUs) continue

                val normPtsUs = (sample.presentationTimeUs - basePtsUs).coerceAtLeast(0L)
                if (normPtsUs > (maxPtsUs - basePtsUs)) {
                    maxPtsUs = sample.presentationTimeUs
                }

                val normalized = ReplaySample(
                    trackIndex = sample.trackIndex,
                    data = sample.data,
                    presentationTimeUs = normPtsUs,
                    flags = sample.flags
                )
                normalizedSamples.add(normalized)
                snapshotBytes += sample.sizeBytes
            }

            val durationMs = ((maxPtsUs - basePtsUs) / 1000L).coerceAtLeast(0L)

            Log.i(tag, "Replay buffer snapshot created: ${normalizedSamples.size} samples, ${durationMs}ms, ${snapshotBytes / 1024} KB")

            return ReplaySnapshot(
                videoFormat = vFormat,
                audioFormat = audioFormat,
                samples = normalizedSamples,
                durationMs = durationMs,
                totalBytes = snapshotBytes
            )
        }
    }

    /**
     * Clears the entire buffer and resets byte tracking.
     */
    fun clear() {
        lock.withLock {
            samples.clear()
            totalBytes = 0L
            lastVideoPtsUs = 0L
            Log.i(tag, "Replay ring buffer cleared")
        }
    }

    val currentSizeBytes: Long
        get() = lock.withLock { totalBytes }

    val sampleCount: Int
        get() = lock.withLock { samples.size }

    val currentDurationMs: Long
        get() = lock.withLock {
            if (samples.isEmpty()) return 0L
            var firstKeyframePtsUs: Long? = null
            for (sample in samples) {
                if (sample.isKeyframe) {
                    firstKeyframePtsUs = sample.presentationTimeUs
                    break
                }
            }
            if (firstKeyframePtsUs != null) {
                ((lastVideoPtsUs - firstKeyframePtsUs) / 1000L).coerceAtLeast(0L)
            } else {
                0L
            }
        }
}

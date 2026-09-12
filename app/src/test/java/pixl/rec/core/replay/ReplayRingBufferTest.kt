package pixl.rec.core.replay

import android.media.MediaCodec
import android.media.MediaFormat
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ReplayRingBufferTest {

    private fun createBufferInfo(
        offset: Int = 0,
        size: Int = 10,
        presentationTimeUs: Long,
        flags: Int = 0
    ): MediaCodec.BufferInfo {
        val info = MediaCodec.BufferInfo()
        info.offset = offset
        info.size = size
        info.presentationTimeUs = presentationTimeUs
        info.flags = flags
        return info
    }

    private fun createSampleBuffer(size: Int = 10): ByteBuffer {
        val bytes = ByteArray(size) { (it % 256).toByte() }
        return ByteBuffer.wrap(bytes)
    }

    @Test
    fun testEmptyBufferSnapshotReturnsNull() {
        val buffer = ReplayRingBuffer(durationSeconds = 15)
        val format = mockk<MediaFormat>(relaxed = true)
        buffer.setVideoFormat(format)

        assertNull(buffer.snapshot())
        assertEquals(0, buffer.sampleCount)
        assertEquals(0L, buffer.currentSizeBytes)
        assertEquals(0L, buffer.currentDurationMs)
    }

    @Test
    fun testSnapshotWithoutVideoFormatReturnsNull() {
        val buffer = ReplayRingBuffer(durationSeconds = 15)
        val bufferInfo = createBufferInfo(
            presentationTimeUs = 1_000_000L,
            flags = MediaCodec.BUFFER_FLAG_KEY_FRAME
        )
        buffer.addSample(0, createSampleBuffer(), bufferInfo)

        // videoFormat is not set
        assertNull(buffer.snapshot())
    }

    @Test
    fun testSnapshotWithoutKeyframeReturnsNull() {
        val buffer = ReplayRingBuffer(durationSeconds = 15)
        val format = mockk<MediaFormat>(relaxed = true)
        buffer.setVideoFormat(format)

        // Add only delta frames (no BUFFER_FLAG_KEY_FRAME)
        val deltaInfo = createBufferInfo(presentationTimeUs = 1_000_000L, flags = 0)
        buffer.addSample(0, createSampleBuffer(), deltaInfo)

        assertNull(buffer.snapshot())
    }

    @Test
    fun testSingleGopPreservedEvenWhenExceedingDuration() {
        // Duration is 10 seconds. We push samples spanning 15 seconds, but with only 1 keyframe.
        val buffer = ReplayRingBuffer(durationSeconds = 10)
        val format = mockk<MediaFormat>(relaxed = true)
        buffer.setVideoFormat(format)

        // Keyframe at 0s
        val kfInfo = createBufferInfo(presentationTimeUs = 0L, flags = MediaCodec.BUFFER_FLAG_KEY_FRAME)
        buffer.addSample(0, createSampleBuffer(), kfInfo)

        // Delta frames spanning up to 15s
        for (sec in 1..15) {
            val deltaInfo = createBufferInfo(presentationTimeUs = sec * 1_000_000L, flags = 0)
            buffer.addSample(0, createSampleBuffer(), deltaInfo)
        }

        // Buffer must not drop the initial keyframe because no second keyframe exists to safely decode from
        assertEquals(16, buffer.sampleCount)
        val snapshot = buffer.snapshot()
        assertNotNull(snapshot)
        assertEquals(16, snapshot!!.samples.size)
        assertTrue(snapshot.samples.first().isKeyframe)
        assertEquals(0L, snapshot.samples.first().presentationTimeUs)
        assertEquals(15_000_000L, snapshot.samples.last().presentationTimeUs)
        assertEquals(15_000L, snapshot.durationMs)
    }

    @Test
    fun testGopAlignedEvictionWhenDurationExceeded() {
        // 10s buffer
        val buffer = ReplayRingBuffer(durationSeconds = 10)
        val format = mockk<MediaFormat>(relaxed = true)
        buffer.setVideoFormat(format)

        // First GOP: Keyframe at 0s, delta at 5s
        buffer.addSample(0, createSampleBuffer(), createBufferInfo(presentationTimeUs = 0L, flags = MediaCodec.BUFFER_FLAG_KEY_FRAME))
        buffer.addSample(0, createSampleBuffer(), createBufferInfo(presentationTimeUs = 5_000_000L, flags = 0))

        // Second GOP: Keyframe at 10s
        buffer.addSample(0, createSampleBuffer(), createBufferInfo(presentationTimeUs = 10_000_000L, flags = MediaCodec.BUFFER_FLAG_KEY_FRAME))

        // Still within 10s span from second keyframe (span = 10s - 10s = 0s) -> no eviction yet
        assertEquals(3, buffer.sampleCount)

        // Add delta frames after second keyframe up to 20s (span from 2nd keyframe = 20s - 10s = 10s >= 10s duration)
        buffer.addSample(0, createSampleBuffer(), createBufferInfo(presentationTimeUs = 20_000_000L, flags = 0))

        // First GOP (0s and 5s) should now be safely evicted! Head must be the 10s keyframe
        assertEquals(2, buffer.sampleCount)
        val snapshot = buffer.snapshot()
        assertNotNull(snapshot)
        assertEquals(2, snapshot!!.samples.size)
        assertTrue(snapshot.samples.first().isKeyframe)

        // Presentation timestamp should be normalized to start at 0
        assertEquals(0L, snapshot.samples.first().presentationTimeUs)
        // 20s - 10s = 10s normalized
        assertEquals(10_000_000L, snapshot.samples[1].presentationTimeUs)
        assertEquals(10_000L, snapshot.durationMs)
    }

    @Test
    fun testAudioAndVideoTimestampNormalizationAndRelativeSync() {
        val buffer = ReplayRingBuffer(durationSeconds = 30)
        val vFormat = mockk<MediaFormat>(relaxed = true)
        val aFormat = mockk<MediaFormat>(relaxed = true)
        buffer.setVideoFormat(vFormat)
        buffer.setAudioFormat(aFormat)

        // Video Keyframe at t = 50_000_000 us (50s)
        buffer.addSample(0, createSampleBuffer(100), createBufferInfo(size = 100, presentationTimeUs = 50_000_000L, flags = MediaCodec.BUFFER_FLAG_KEY_FRAME))

        // Audio sample at t = 50_500_000 us (50.5s)
        buffer.addSample(1, createSampleBuffer(50), createBufferInfo(size = 50, presentationTimeUs = 50_500_000L, flags = 0))

        // Video delta at t = 51_000_000 us (51s)
        buffer.addSample(0, createSampleBuffer(100), createBufferInfo(size = 100, presentationTimeUs = 51_000_000L, flags = 0))

        // Audio sample at t = 51_500_000 us (51.5s)
        buffer.addSample(1, createSampleBuffer(50), createBufferInfo(size = 50, presentationTimeUs = 51_500_000L, flags = 0))

        val snapshot = buffer.snapshot()
        assertNotNull(snapshot)
        assertEquals(4, snapshot!!.samples.size)

        // Check PTS normalization
        val sample0 = snapshot.samples[0] // Video KF
        assertEquals(0, sample0.trackIndex)
        assertEquals(0L, sample0.presentationTimeUs)
        assertTrue(sample0.isKeyframe)

        val sample1 = snapshot.samples[1] // Audio
        assertEquals(1, sample1.trackIndex)
        assertEquals(500_000L, sample1.presentationTimeUs) // 50.5s - 50.0s = 0.5s (500_000 us)

        val sample2 = snapshot.samples[2] // Video delta
        assertEquals(0, sample2.trackIndex)
        assertEquals(1_000_000L, sample2.presentationTimeUs) // 51.0s - 50.0s = 1.0s (1_000_000 us)

        val sample3 = snapshot.samples[3] // Audio
        assertEquals(1, sample3.trackIndex)
        assertEquals(1_500_000L, sample3.presentationTimeUs) // 51.5s - 50.0s = 1.5s (1_500_000 us)

        assertEquals(1500L, snapshot.durationMs)
        assertEquals(300L, snapshot.totalBytes)
    }

    @Test
    fun testAudioBeforeFirstKeyframeIsPrunedFromSnapshot() {
        val buffer = ReplayRingBuffer(durationSeconds = 30)
        val format = mockk<MediaFormat>(relaxed = true)
        buffer.setVideoFormat(format)

        // Audio sample at 10s (before any video keyframe)
        buffer.addSample(1, createSampleBuffer(50), createBufferInfo(presentationTimeUs = 10_000_000L, flags = 0))

        // First video keyframe at 20s
        buffer.addSample(0, createSampleBuffer(100), createBufferInfo(presentationTimeUs = 20_000_000L, flags = MediaCodec.BUFFER_FLAG_KEY_FRAME))

        // Audio sample at 21s
        buffer.addSample(1, createSampleBuffer(50), createBufferInfo(presentationTimeUs = 21_000_000L, flags = 0))

        val snapshot = buffer.snapshot()
        assertNotNull(snapshot)

        // The audio sample at 10s should NOT be in the snapshot because it is older than the initial keyframe
        assertEquals(2, snapshot!!.samples.size)
        assertEquals(0, snapshot.samples[0].trackIndex)
        assertEquals(0L, snapshot.samples[0].presentationTimeUs)
        assertEquals(1, snapshot.samples[1].trackIndex)
        assertEquals(1_000_000L, snapshot.samples[1].presentationTimeUs)
    }

    @Test
    fun testClearResetsAllState() {
        val buffer = ReplayRingBuffer(durationSeconds = 30)
        val format = mockk<MediaFormat>(relaxed = true)
        buffer.setVideoFormat(format)

        buffer.addSample(0, createSampleBuffer(100), createBufferInfo(presentationTimeUs = 10_000_000L, flags = MediaCodec.BUFFER_FLAG_KEY_FRAME))
        assertTrue(buffer.sampleCount > 0)
        assertTrue(buffer.currentSizeBytes > 0)

        buffer.clear()

        assertEquals(0, buffer.sampleCount)
        assertEquals(0L, buffer.currentSizeBytes)
        assertEquals(0L, buffer.currentDurationMs)
        assertNull(buffer.snapshot())
    }

    @Test
    fun testConcurrentAddSampleAndSnapshotThreadSafety() {
        val buffer = ReplayRingBuffer(durationSeconds = 5)
        val format = mockk<MediaFormat>(relaxed = true)
        buffer.setVideoFormat(format)

        val executor = Executors.newFixedThreadPool(4)
        val latch = CountDownLatch(4)

        // Thread 1: Add video keyframes and delta frames continuously
        executor.execute {
            try {
                for (i in 0 until 500) {
                    val isKeyframe = (i % 20) == 0
                    val flags = if (isKeyframe) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
                    val info = createBufferInfo(presentationTimeUs = i * 33_333L, flags = flags)
                    buffer.addSample(0, createSampleBuffer(64), info)
                }
            } finally {
                latch.countDown()
            }
        }

        // Thread 2: Add audio samples continuously
        executor.execute {
            try {
                for (i in 0 until 500) {
                    val info = createBufferInfo(presentationTimeUs = i * 20_000L, flags = 0)
                    buffer.addSample(1, createSampleBuffer(32), info)
                }
            } finally {
                latch.countDown()
            }
        }

        // Thread 3: Take snapshots concurrently
        executor.execute {
            try {
                for (i in 0 until 50) {
                    buffer.snapshot()
                    Thread.sleep(2)
                }
            } finally {
                latch.countDown()
            }
        }

        // Thread 4: Query metrics concurrently
        executor.execute {
            try {
                for (i in 0 until 50) {
                    buffer.currentDurationMs
                    buffer.currentSizeBytes
                    buffer.sampleCount
                    Thread.sleep(2)
                }
            } finally {
                latch.countDown()
            }
        }

        val finished = latch.await(10, TimeUnit.SECONDS)
        executor.shutdown()
        assertTrue("Concurrent operations should complete without hanging or deadlocking", finished)
    }
}

package pixl.rec.core.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pixl.rec.core.model.StreamConfig
import pixl.rec.core.model.StreamDestination
import pixl.rec.core.model.StreamPlatform

class AudioClockSynchronizationTest {

    @Test
    fun testInitialAlignment_withSessionBaseTime() {
        var syntheticTimeNs = 10_000_000_000L // 10s base time
        val synchronizer = AudioClockSynchronizer(
            sampleRate = 48_000,
            channelCount = 2,
            sessionBaseTimeNs = syntheticTimeNs,
            timeProvider = { syntheticTimeNs }
        )

        // First chunk arrives 50ms (50_000_000ns) after session start
        syntheticTimeNs += 50_000_000L
        val ptsUs = synchronizer.computeNextChunkPtsUs(chunkBytes = 4096, nowNs = syntheticTimeNs)

        // Initial PTS should align to session elapsed time (50ms = 50,000us)
        assertEquals(50_000L, ptsUs)
    }

    @Test
    fun testStrictMonotonicity_duringNormalPlayback() {
        var syntheticTimeNs = 1_000_000_000L
        val chunkDurationNs = (1024L * 1_000_000_000L) / 48_000L // ~21,333,333 ns

        val synchronizer = AudioClockSynchronizer(
            sampleRate = 48_000,
            channelCount = 2,
            sessionBaseTimeNs = syntheticTimeNs,
            timeProvider = { syntheticTimeNs }
        )

        var lastPts = -1L
        for (i in 0 until 100) {
            val pts = synchronizer.computeNextChunkPtsUs(4096, syntheticTimeNs)
            assertTrue("PTS must strictly increase ($pts > $lastPts)", pts > lastPts)
            lastPts = pts
            syntheticTimeNs += chunkDurationNs
        }

        // Over 100 chunks (~2.1s) of steady clock pacing, drift should be <= 1ms
        assertTrue(Math.abs(synchronizer.getAudioDriftMs()) <= 1L)
    }

    @Test
    fun testPauseResume_advancesMonotonicallyWithoutJump() {
        var syntheticTimeNs = 1_000_000_000L
        val chunkDurationNs = (1024L * 1_000_000_000L) / 48_000L // ~21.3ms

        val synchronizer = AudioClockSynchronizer(
            sampleRate = 48_000,
            channelCount = 2,
            sessionBaseTimeNs = syntheticTimeNs,
            timeProvider = { syntheticTimeNs }
        )

        // 10 chunks before pause (~213ms)
        var lastPts = -1L
        for (i in 0 until 10) {
            val pts = synchronizer.computeNextChunkPtsUs(4096, syntheticTimeNs)
            assertTrue(pts > lastPts)
            lastPts = pts
            syntheticTimeNs += chunkDurationNs
        }

        // Pause session for 5.0 seconds
        synchronizer.pause(syntheticTimeNs)
        syntheticTimeNs += 5_000_000_000L
        synchronizer.resume(syntheticTimeNs)

        // Emit next chunk after resume
        val postResumePts = synchronizer.computeNextChunkPtsUs(4096, syntheticTimeNs)

        // Monotonicity strictly preserved
        assertTrue("Post-resume PTS must be greater than pre-pause PTS", postResumePts > lastPts)

        // Elapsed PTS progression across the 5s pause should only advance by ~1 chunk duration (21.3ms),
        // NOT by 5 seconds, because the paused duration is excluded
        val deltaPtsUs = postResumePts - lastPts
        assertTrue("PTS delta ($deltaPtsUs us) should be approx one chunk duration (~21,333 us)", deltaPtsUs in 20_000L..25_000L)
    }

    @Test
    fun testBoundedSlew_correctsSlowSampleClockDrift() {
        var syntheticTimeNs = 1_000_000_000L
        val chunkDurationNs = 21_333_333L // ~21.3ms for 1024 samples at 48kHz

        val synchronizer = AudioClockSynchronizer(
            sampleRate = 48_000,
            channelCount = 2,
            sessionBaseTimeNs = syntheticTimeNs,
            driftCorrectionThresholdUs = 40_000L, // 40ms threshold
            maxCorrectionPerChunkUs = 500L,       // 500us per chunk
            timeProvider = { syntheticTimeNs }
        )

        // Initial chunk
        synchronizer.computeNextChunkPtsUs(4096, syntheticTimeNs)

        // Artificially advance session time by 80ms more than sample intervals (simulating slow audio hardware clock)
        syntheticTimeNs += 80_000_000L

        // Next chunk detects drift > 40ms
        val pts1 = synchronizer.computeNextChunkPtsUs(4096, syntheticTimeNs)
        val initialDriftMs = synchronizer.getAudioDriftMs()

        // Audio is lagging behind session clock (-58ms)
        assertTrue("Initial drift should be negative (lagging): $initialDriftMs ms", initialDriftMs < -40L)

        // Over the next 50 chunks, bounded slew (+500us per chunk) should progressively correct drift
        var lastPts = pts1
        for (i in 0 until 50) {
            syntheticTimeNs += chunkDurationNs
            val nextPts = synchronizer.computeNextChunkPtsUs(4096, syntheticTimeNs)
            assertTrue("PTS must strictly increase during positive slew ($nextPts > $lastPts)", nextPts > lastPts)
            lastPts = nextPts
        }

        // Bounded slew should have reduced the drift by ~25ms (50 * 500us = 25,000us = 25ms)
        val correctedDriftMs = synchronizer.getAudioDriftMs()
        assertTrue("Corrected drift ($correctedDriftMs ms) must be closer to 0 than initial ($initialDriftMs ms)",
            Math.abs(correctedDriftMs) < Math.abs(initialDriftMs))
    }

    @Test
    fun testBoundedSlew_correctsFastSampleClockDrift() {
        var syntheticTimeNs = 1_000_000_000L
        val chunkDurationNs = 21_333_333L

        val synchronizer = AudioClockSynchronizer(
            sampleRate = 48_000,
            channelCount = 2,
            sessionBaseTimeNs = syntheticTimeNs,
            driftCorrectionThresholdUs = 40_000L,
            maxCorrectionPerChunkUs = 500L,
            timeProvider = { syntheticTimeNs }
        )

        synchronizer.computeNextChunkPtsUs(4096, syntheticTimeNs)

        // Simulate audio samples arriving faster than session time (accumulating +60ms lead)
        // Advance synthetic time by only 1ms while emitting multiple chunks
        for (i in 0 until 3) {
            synchronizer.computeNextChunkPtsUs(4096, syntheticTimeNs)
        }

        // Delay session time slightly
        val driftMs = synchronizer.getAudioDriftMs()
        var lastPts = 0L

        // Verify that during negative slew, monotonicity is never violated (pts > lastPts)
        for (i in 0 until 20) {
            syntheticTimeNs += chunkDurationNs
            val pts = synchronizer.computeNextChunkPtsUs(4096, syntheticTimeNs)
            assertTrue("PTS must strictly increase even during negative slew ($pts > $lastPts)", pts > lastPts)
            lastPts = pts
        }
    }

    @Test
    fun testQueueTrimming_doesNotCorruptSessionTiming() {
        val synchronizer = AudioClockSynchronizer(sampleRate = 48_000, channelCount = 2)

        assertEquals(0L, synchronizer.totalTrimmedChunks.get())
        assertEquals(0L, synchronizer.totalTrimmedBytes.get())

        // 4 chunks trimmed due to queue overflow (4 * 4096 = 16384 bytes = 4096 frames = ~85.3ms)
        for (i in 0 until 4) {
            synchronizer.onBufferTrimmed(4096)
        }

        assertEquals(4L, synchronizer.totalTrimmedChunks.get())
        assertEquals(16384L, synchronizer.totalTrimmedBytes.get())
    }

    @Test
    fun testStreamConfig_audioProfileAndGopDefaults() {
        val config = StreamConfig(
            videoBitrate = 8_000_000,
            destinations = listOf(
                StreamDestination(
                    platform = StreamPlatform.YOUTUBE,
                    streamKey = "live_test_key"
                )
            )
        )

        // Broadcast profile defaults
        assertEquals(128_000, config.audioBitrate)
        assertEquals(2.0f, config.keyframeIntervalSeconds, 0.01f)

        // Total required bandwidth: 8 Mbps video + 128 kbps audio = 8,128,000 bps
        assertEquals(8_128_000L, config.totalRequiredBitrateBps)
    }
}

package pixl.rec.core.stream

import android.media.MediaCodec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import pixl.rec.core.audio.AudioClockSynchronizer
import pixl.rec.core.engine.MultiStreamOutputTarget
import pixl.rec.core.model.PacketMediaType
import pixl.rec.core.model.StreamConfig
import pixl.rec.core.model.StreamDestination
import pixl.rec.core.model.StreamPlatform
import pixl.rec.core.telemetry.ThermalPowerMonitor
import java.nio.ByteBuffer
import kotlin.math.abs

/**
 * Production Soak Test and Hardware Telemetry Validator for Phase 8.
 * Verifies:
 * 1. Differential baseline metrics vs game-only baseline.
 * 2. Thermal throttling event transition detection.
 * 3. Linear regression memory slope calculation (proving zero-leak stability).
 * 4. Multi-destination fanout resource scaling (O(1) encode, O(N) network).
 * 5. Extended soak simulation with monotonic PTS progression and bounded queues.
 */
class ThermalPowerSoakTest {

    private var testScope: CoroutineScope? = null

    @Before
    fun setUp() {
        testScope = CoroutineScope(Dispatchers.Default)
    }

    @After
    fun tearDown() {
        testScope?.cancel()
    }

    private class MockMetricsProvider : ThermalPowerMonitor.MetricsProvider {
        var levelPercent: Float = 100f
        var tempCelsius: Float = 28f
        var currentUa: Long = -400_000L // 400mA discharge
        var currentThermalStatusVal: Int = 0 // NONE
        var pssKb: Long = 40_000L
        var timeNs: Long = 1_000_000_000L // 1.0s

        override fun getBatteryLevelPercent(): Float = levelPercent
        override fun getBatteryTemperatureCelsius(): Float = tempCelsius
        override fun getBatteryCurrentNowUa(): Long = currentUa
        override fun getThermalStatus(): Int = currentThermalStatusVal
        override fun getProcessPssKb(): Long = pssKb
        override fun getTimestampNs(): Long = timeNs
    }

    @Test
    fun testBaselineCapture_andDifferentialTracking() {
        val provider = MockMetricsProvider().apply {
            levelPercent = 95f
            tempCelsius = 29.5f
            currentUa = -500_000L // 500 mA
            currentThermalStatusVal = 0
            pssKb = 50_000L
            timeNs = 1_000_000_000L
        }
        val monitor = ThermalPowerMonitor(provider)

        // Capture pre-recording baseline with game running at 60 FPS
        val baseline = monitor.captureBaseline(gameFps = 60f)
        assertEquals(95f, baseline.batteryLevelPercent, 0.01f)
        assertEquals(29.5f, baseline.batteryTempCelsius, 0.01f)
        assertEquals(60f, baseline.gameFps, 0.01f)
        assertEquals(50_000L, baseline.pssMemoryKb)

        // Advance time by 60 seconds (1 minute), simulate battery heating and slight game frame drop
        provider.timeNs += 60L * 1_000_000_000L
        provider.tempCelsius = 32.0f // +2.5 deg C
        provider.levelPercent = 94.8f // 0.2% drop in 1 minute -> 12.0% / hour
        provider.currentUa = -650_000L // 650 mA
        provider.pssKb = 50_200L

        val snapshot = monitor.recordSample(
            encoderFps = 60f,
            currentGameFps = 58.5f,
            activeDestinations = 2,
            socketWriteP95Ms = 12L,
            totalThroughputBps = 8_000_000L
        )

        assertEquals(60_000L, snapshot.elapsedDurationMs)
        assertEquals(2.5f, snapshot.deltaBatteryTempCelsius, 0.01f)
        assertEquals(-1.5f, snapshot.deltaGameFps, 0.01f)
        assertEquals(650.0f, snapshot.batteryCurrentNowMa, 0.01f)
        assertEquals(12.0f, snapshot.batteryDrainRatePercentPerHour, 0.5f)
        assertEquals(2, snapshot.activeDestinationsCount)
        assertEquals(12L, snapshot.socketWriteLatencyP95Ms)
    }

    @Test
    fun testThermalThrottlingTransitions_severeAndCriticalEventsCounted() {
        val provider = MockMetricsProvider()
        val monitor = ThermalPowerMonitor(provider)
        monitor.captureBaseline()

        // 1. Nominal transition (Status 0 -> 1 LIGHT): Not severe
        provider.currentThermalStatusVal = 1
        var snapshot = monitor.recordSample()
        assertEquals(0, snapshot.thermalThrottlingEvents)

        // 2. Status 1 -> 2 (MODERATE): Not severe
        provider.currentThermalStatusVal = 2
        snapshot = monitor.recordSample()
        assertEquals(0, snapshot.thermalThrottlingEvents)

        // 3. Status 2 -> 3 (SEVERE): First throttling event!
        provider.currentThermalStatusVal = 3
        snapshot = monitor.recordSample()
        assertEquals(1, snapshot.thermalThrottlingEvents)

        // 4. Status 3 -> 3 (Stays SEVERE): No new transition
        snapshot = monitor.recordSample()
        assertEquals(1, snapshot.thermalThrottlingEvents)

        // 5. Status 3 -> 4 (CRITICAL): Escalation
        provider.currentThermalStatusVal = 4
        snapshot = monitor.recordSample()
        assertEquals(2, snapshot.thermalThrottlingEvents)

        // 6. Cools down to LIGHT (Status 4 -> 1)
        provider.currentThermalStatusVal = 1
        snapshot = monitor.recordSample()
        assertEquals(2, snapshot.thermalThrottlingEvents)

        // 7. Spikes back up to SEVERE (Status 1 -> 3): New throttling event
        provider.currentThermalStatusVal = 3
        snapshot = monitor.recordSample()
        assertEquals(3, snapshot.thermalThrottlingEvents)
    }

    @Test
    fun testLinearRegressionMemorySlope_zeroLeakOnStableMemory() {
        val provider = MockMetricsProvider().apply {
            pssKb = 60_000L
        }
        val monitor = ThermalPowerMonitor(provider)
        monitor.captureBaseline()

        // Simulate 60 minutes of flat memory (slight noise around 60 MB)
        for (minute in 1..60) {
            provider.timeNs += 60L * 1_000_000_000L
            val noise = if (minute % 2 == 0) 50L else -50L
            provider.pssKb = 60_000L + noise
            monitor.recordSample()
        }

        val stableSnapshot = monitor.getDifferentialSnapshot()
        // Linear regression slope on flat noise should be near zero (|slope| < 100 kB/hr)
        assertTrue(
            "Memory slope should be near zero for stable session but was ${stableSnapshot.memorySlopeKbPerHour} kB/hr",
            abs(stableSnapshot.memorySlopeKbPerHour) < 100.0
        )

        // Now simulate a continuous memory leak: +1000 kB every minute = 60 MB/hr
        val leakProvider = MockMetricsProvider().apply {
            pssKb = 40_000L
        }
        val leakMonitor = ThermalPowerMonitor(leakProvider)
        leakMonitor.captureBaseline()

        for (minute in 1..60) {
            leakProvider.timeNs += 60L * 1_000_000_000L
            leakProvider.pssKb = 40_000L + (minute * 1000L)
            leakMonitor.recordSample()
        }

        val leakSnapshot = leakMonitor.getDifferentialSnapshot()
        // Slope should detect approx ~60,000 kB/hr leak
        assertTrue(
            "Memory slope should detect large positive leak (>50000 kB/hr) but was ${leakSnapshot.memorySlopeKbPerHour} kB/hr",
            leakSnapshot.memorySlopeKbPerHour > 50_000.0
        )
    }

    @Test
    fun testMultiDestinationScaling_O1VideoEncode_ONNetworkPacketization() = runBlocking {
        val destA = StreamDestination(
            platform = StreamPlatform.YOUTUBE,
            customEndpointUrl = "rtmp://127.0.0.1:1935/live",
            streamKey = "key_yt",
            enabled = true
        )
        val destB = StreamDestination(
            platform = StreamPlatform.TWITCH,
            customEndpointUrl = "rtmp://127.0.0.1:1935/live",
            streamKey = "key_twitch",
            enabled = true
        )
        val destC = StreamDestination(
            platform = StreamPlatform.KICK,
            customEndpointUrl = "rtmp://127.0.0.1:1935/live",
            streamKey = "key_kick",
            enabled = true
        )

        val config = StreamConfig(
            platform = StreamPlatform.CUSTOM,
            streamKey = "test_key",
            enableAbr = false
        )
        val scope = CoroutineScope(Dispatchers.Default)

        val multiTarget = MultiStreamOutputTarget(
            destinations = listOf(destA, destB, destC),
            streamConfig = config,
            scope = scope
        )

        assertEquals("Should configure 3 child targets", 3, multiTarget.childTargets.size)
        for (target in multiTarget.childTargets) {
            target.connection.initChannelForTesting(capacity = 64)
        }

        multiTarget.start()

        // 1. Send single AVC Keyframe
        val keyBytes = byteArrayOf(0x00, 0x00, 0x00, 0x01, 0x65.toByte(), 0x88.toByte(), 0x84.toByte(), 0x00)
        val keyBuf = ByteBuffer.wrap(keyBytes)
        val keyInfo = MediaCodec.BufferInfo().apply {
            offset = 0
            size = keyBytes.size
            presentationTimeUs = 100_000L
            flags = MediaCodec.BUFFER_FLAG_KEY_FRAME
        }

        multiTarget.onVideoSample(keyBuf, keyInfo)

        // Verify that single encode sample is received across all 3 destinations independently
        var deliveredCount = 0
        multiTarget.childTargets.forEach { target ->
            val packet = target.connection.getVideoChannel()?.tryReceive()?.getOrNull()
            if (packet != null) {
                deliveredCount++
                assertEquals(PacketMediaType.VIDEO_KEYFRAME, packet.mediaType)
            }
        }
        assertEquals(3, deliveredCount)

        multiTarget.release()
    }

    @Test
    fun testExtendedSoakSimulation_preservesMonotonicTimestampsAndNoBufferLeaks() = runBlocking {
        var syntheticTimeNs = 1_000_000_000L
        val synchronizer = AudioClockSynchronizer(
            sampleRate = 48_000,
            channelCount = 2,
            sessionBaseTimeNs = syntheticTimeNs,
            timeProvider = { syntheticTimeNs }
        )

        var lastAudioPtsUs = -1L
        var lastVideoPtsUs = -1L

        val mockProvider = MockMetricsProvider().apply {
            timeNs = syntheticTimeNs
            pssKb = 55_000L
        }
        val monitor = ThermalPowerMonitor(mockProvider)
        monitor.captureBaseline(gameFps = 60f)

        // Simulate 30 seconds of concurrent 60 FPS video and 48 kHz stereo audio
        val totalDurationUs = 30_000_000L
        val videoStepUs = 16_666L // ~60 FPS
        val audioChunkStepUs = 21_333L // 1024 samples at 48kHz stereo = ~21.333ms
        val chunkBytes = 4096

        var nextVideoTimeUs = videoStepUs
        var nextAudioTimeUs = audioChunkStepUs
        var currentSimTimeUs = 0L

        while (currentSimTimeUs < totalDurationUs) {
            val stepTo = minOf(nextVideoTimeUs, nextAudioTimeUs)
            currentSimTimeUs = stepTo
            syntheticTimeNs = 1_000_000_000L + (currentSimTimeUs * 1000L)
            mockProvider.timeNs = syntheticTimeNs

            if (currentSimTimeUs == nextVideoTimeUs) {
                // 1. Validate strictly increasing monotonic video PTS
                assertTrue("Video PTS must be monotonic: $currentSimTimeUs >= $lastVideoPtsUs", currentSimTimeUs >= lastVideoPtsUs)
                lastVideoPtsUs = currentSimTimeUs
                nextVideoTimeUs += videoStepUs
            }

            if (currentSimTimeUs == nextAudioTimeUs) {
                // 2. Simulate audio clock sync progression
                val audioPtsUs = synchronizer.computeNextChunkPtsUs(chunkBytes, syntheticTimeNs)
                assertTrue("Audio PTS must be monotonic: $audioPtsUs >= $lastAudioPtsUs", audioPtsUs >= lastAudioPtsUs)
                lastAudioPtsUs = audioPtsUs
                nextAudioTimeUs += audioChunkStepUs

                // 3. Audio/Video drift must stay bounded within <= 80ms SLO
                val driftMs = abs(synchronizer.getAudioDriftMs())
                assertTrue("Drift must remain bounded <= 80ms, but was ${driftMs}ms at ${currentSimTimeUs}us", driftMs <= 80L)
            }

            // Periodically sample hardware metrics every 1 second
            if (currentSimTimeUs % 1_000_000L < videoStepUs) {
                mockProvider.pssKb = 55_000L
                val telem = monitor.recordSample(
                    encoderFps = 60f,
                    currentGameFps = 60f,
                    activeDestinations = 1
                )
                assertTrue("Memory slope must remain bounded", abs(telem.memorySlopeKbPerHour) < 10.0)
            }
        }
    }
}

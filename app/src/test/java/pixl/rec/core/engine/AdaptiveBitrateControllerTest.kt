package pixl.rec.core.engine

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pixl.rec.core.model.AbrTelemetrySignal
import pixl.rec.core.model.RateAdjustmentReason
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

@OptIn(ExperimentalCoroutinesApi::class)
class AdaptiveBitrateControllerTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Test
    fun testMultiplicativeDecrease_onHardDrops() {
        val adjustedBitrate = AtomicInteger(0)
        val syncFrameRequested = AtomicBoolean(false)
        var syntheticTime = 1000L

        val abr = AdaptiveBitrateController(
            scope = testScope,
            initialBitrateBps = 10_000_000,
            minBitrateBps = 2_000_000,
            maxBitrateBps = 14_000_000,
            timeProvider = { syntheticTime },
            onBitrateAdjusted = { adjustedBitrate.set(it) },
            onRequestSyncFrame = { syncFrameRequested.set(true) }
        )

        assertEquals(10_000_000, abr.currentBitrateBps.value)
        assertEquals(UplinkHealth.CLEAN, abr.uplinkHealth.value)
        assertEquals(RateAdjustmentReason.NONE, abr.lastAdjustmentReason.value)

        // Simulate 5 dropped frames
        abr.evaluate(AbrTelemetrySignal(newDrops = 5))

        // Should reduce by 25% (10M * 0.75 = 7.5M)
        assertEquals(7_500_000, abr.currentBitrateBps.value)
        assertEquals(7_500_000, adjustedBitrate.get())
        assertTrue(syncFrameRequested.get())
        assertEquals(UplinkHealth.ADAPTING, abr.uplinkHealth.value)
        assertEquals(RateAdjustmentReason.PACKET_DROPS, abr.lastAdjustmentReason.value)
    }

    @Test
    fun testMinBitrateFloorClamping() {
        val abr = AdaptiveBitrateController(
            scope = testScope,
            initialBitrateBps = 2_500_000,
            minBitrateBps = 2_000_000,
            maxBitrateBps = 10_000_000,
            onBitrateAdjusted = {},
            onRequestSyncFrame = {}
        )

        // 2.5M * 0.75 = 1.875M, which is below minBitrate (2M)
        abr.evaluate(AbrTelemetrySignal(newDrops = 10))

        assertEquals(2_000_000, abr.currentBitrateBps.value)
        assertEquals(UplinkHealth.CONGESTED, abr.uplinkHealth.value)
    }

    @Test
    fun testEarlyDecrease_onHighQueueAge_beforeDropsOccur() {
        val adjustedBitrate = AtomicInteger(0)
        val syncFrameRequested = AtomicBoolean(false)
        var syntheticTime = 1000L

        val abr = AdaptiveBitrateController(
            scope = testScope,
            initialBitrateBps = 10_000_000,
            minBitrateBps = 2_000_000,
            maxBitrateBps = 14_000_000,
            timeProvider = { syntheticTime },
            onBitrateAdjusted = { adjustedBitrate.set(it) },
            onRequestSyncFrame = { syncFrameRequested.set(true) }
        )

        // High queue latency (500ms >= 400ms threshold) with zero dropped frames
        abr.evaluate(AbrTelemetrySignal(newDrops = 0, queueAgeMs = 500))

        // Early step down: -15% (10M * 0.85 = 8.5M)
        assertEquals(8_500_000, abr.currentBitrateBps.value)
        assertEquals(8_500_000, adjustedBitrate.get())
        assertTrue(syncFrameRequested.get())
        assertEquals(UplinkHealth.ADAPTING, abr.uplinkHealth.value)
        assertEquals(RateAdjustmentReason.HIGH_QUEUE_AGE, abr.lastAdjustmentReason.value)
    }

    @Test
    fun testEarlyDecrease_onHighWriteLatency() {
        val adjustedBitrate = AtomicInteger(0)
        var syntheticTime = 1000L

        val abr = AdaptiveBitrateController(
            scope = testScope,
            initialBitrateBps = 10_000_000,
            minBitrateBps = 2_000_000,
            maxBitrateBps = 14_000_000,
            timeProvider = { syntheticTime },
            onBitrateAdjusted = { adjustedBitrate.set(it) }
        )

        // Write latency P95 is 320ms (>= 250ms threshold) with zero drops
        abr.evaluate(AbrTelemetrySignal(newDrops = 0, writeLatencyP95Ms = 320))

        // Early step down: -15% (10M * 0.85 = 8.5M)
        assertEquals(8_500_000, abr.currentBitrateBps.value)
        assertEquals(8_500_000, adjustedBitrate.get())
        assertEquals(RateAdjustmentReason.HIGH_WRITE_LATENCY, abr.lastAdjustmentReason.value)
    }

    @Test
    fun testEarlyDecrease_onBufferSaturation() {
        val adjustedBitrate = AtomicInteger(0)
        var syntheticTime = 1000L

        val abr = AdaptiveBitrateController(
            scope = testScope,
            initialBitrateBps = 10_000_000,
            minBitrateBps = 2_000_000,
            maxBitrateBps = 14_000_000,
            timeProvider = { syntheticTime },
            onBitrateAdjusted = { adjustedBitrate.set(it) }
        )

        // Buffer saturation (2.0 MB >= 1.5 MB threshold)
        abr.evaluate(AbrTelemetrySignal(newDrops = 0, bytesInQueue = 2_000_000L))

        assertEquals(8_500_000, abr.currentBitrateBps.value)
        assertEquals(8_500_000, adjustedBitrate.get())
        assertEquals(RateAdjustmentReason.BUFFER_SATURATION, abr.lastAdjustmentReason.value)
    }

    @Test
    fun testEarlyDecrease_onDestinationReconnecting() {
        val adjustedBitrate = AtomicInteger(0)
        var syntheticTime = 1000L

        val abr = AdaptiveBitrateController(
            scope = testScope,
            initialBitrateBps = 10_000_000,
            minBitrateBps = 2_000_000,
            maxBitrateBps = 14_000_000,
            timeProvider = { syntheticTime },
            onBitrateAdjusted = { adjustedBitrate.set(it) }
        )

        // Healthy destination in reconnect state -> throttle -20%
        abr.evaluate(AbrTelemetrySignal(isAnyHealthyDestinationReconnecting = true))

        assertEquals(8_000_000, abr.currentBitrateBps.value)
        assertEquals(8_000_000, adjustedBitrate.get())
        assertEquals(RateAdjustmentReason.RECONNECT_DRAIN, abr.lastAdjustmentReason.value)
    }

    @Test
    fun testHysteresisAndCooldown_suppressesRepeatedDecreasesForSoftSignals() {
        val stepCount = AtomicInteger(0)
        var syntheticTime = 1000L

        val abr = AdaptiveBitrateController(
            scope = testScope,
            initialBitrateBps = 10_000_000,
            minBitrateBps = 2_000_000,
            maxBitrateBps = 14_000_000,
            decreaseCooldownMs = 3000L,
            timeProvider = { syntheticTime },
            onBitrateAdjusted = { stepCount.incrementAndGet() }
        )

        // T = 1000ms: High queue age causes first step down
        abr.evaluate(AbrTelemetrySignal(queueAgeMs = 450))
        assertEquals(8_500_000, abr.currentBitrateBps.value)
        assertEquals(1, stepCount.get())

        // T = 2000ms (1000ms elapsed < 3000ms cooldown): High queue age arrives again
        syntheticTime = 2000L
        abr.evaluate(AbrTelemetrySignal(queueAgeMs = 480))
        // Cooldown prevents step-down cascading!
        assertEquals(8_500_000, abr.currentBitrateBps.value)
        assertEquals(1, stepCount.get())

        // T = 4500ms (3500ms elapsed > 3000ms cooldown): Congestion still present
        syntheticTime = 4500L
        abr.evaluate(AbrTelemetrySignal(queueAgeMs = 500))
        // Cooldown expired -> second step down permitted (8.5M * 0.85 = 7.225M)
        assertEquals(7_225_000, abr.currentBitrateBps.value)
        assertEquals(2, stepCount.get())
    }

    @Test
    fun testHardDrops_allowDefensiveEmergencyDecreaseAfterOneSecond() {
        val stepCount = AtomicInteger(0)
        var syntheticTime = 1000L

        val abr = AdaptiveBitrateController(
            scope = testScope,
            initialBitrateBps = 10_000_000,
            minBitrateBps = 2_000_000,
            maxBitrateBps = 14_000_000,
            decreaseCooldownMs = 3000L,
            timeProvider = { syntheticTime },
            onBitrateAdjusted = { stepCount.incrementAndGet() }
        )

        // T = 1000ms: First hard drop -> 10M * 0.75 = 7.5M
        abr.evaluate(AbrTelemetrySignal(newDrops = 2))
        assertEquals(7_500_000, abr.currentBitrateBps.value)
        assertEquals(1, stepCount.get())

        // T = 2100ms (1100ms elapsed >= 1000ms emergency drop threshold): Severe packet loss continues!
        syntheticTime = 2100L
        abr.evaluate(AbrTelemetrySignal(newDrops = 5))
        // Defensive decrease triggers: 7.5M * 0.75 = 5.625M
        assertEquals(5_625_000, abr.currentBitrateBps.value)
        assertEquals(2, stepCount.get())
    }

    @Test
    fun testAdditiveIncrease_afterSustainedCleanWindow() {
        val adjustedBitrate = AtomicInteger(0)
        var syntheticTime = 10_000L

        val abr = AdaptiveBitrateController(
            scope = testScope,
            initialBitrateBps = 6_000_000,
            minBitrateBps = 2_000_000,
            maxBitrateBps = 10_000_000,
            cleanConsecutiveChecksRequired = 5,
            increaseCooldownMs = 5000L,
            decreaseCooldownMs = 3000L,
            timeProvider = { syntheticTime },
            onBitrateAdjusted = { adjustedBitrate.set(it) }
        )

        val cleanSignal = AbrTelemetrySignal(
            newDrops = 0,
            queueAgeMs = 50,
            writeLatencyP95Ms = 40,
            bytesInQueue = 100_000L
        )

        // First 4 clean seconds -> no increase yet (needs 5)
        for (i in 1..4) {
            syntheticTime += 1000L
            abr.evaluate(cleanSignal)
            assertEquals(6_000_000, abr.currentBitrateBps.value)
        }

        // 5th clean second -> Additive Increase (+8% or min 500k: max(500k, 480k) = 500k -> 6.5M)
        syntheticTime += 1000L
        abr.evaluate(cleanSignal)
        assertEquals(6_500_000, abr.currentBitrateBps.value)
        assertEquals(6_500_000, adjustedBitrate.get())
        assertEquals(UplinkHealth.CLEAN, abr.uplinkHealth.value)
        assertEquals(RateAdjustmentReason.CONGESTION_AVOIDANCE_PROBE, abr.lastAdjustmentReason.value)
    }

    @Test
    fun testUpwardProbeSuppressed_duringDecreaseCooldown() {
        val adjustedBitrate = AtomicInteger(0)
        var syntheticTime = 1000L

        val abr = AdaptiveBitrateController(
            scope = testScope,
            initialBitrateBps = 6_000_000,
            minBitrateBps = 2_000_000,
            maxBitrateBps = 10_000_000,
            cleanConsecutiveChecksRequired = 2,
            decreaseCooldownMs = 5000L,
            timeProvider = { syntheticTime },
            onBitrateAdjusted = { adjustedBitrate.set(it) }
        )

        // Drop at T = 1000ms -> steps down to 4.5M
        abr.evaluate(AbrTelemetrySignal(newDrops = 1))
        assertEquals(4_500_000, abr.currentBitrateBps.value)

        // T = 2000ms: clean tick
        syntheticTime = 2000L
        abr.evaluate(AbrTelemetrySignal(newDrops = 0, queueAgeMs = 20, writeLatencyP95Ms = 20))

        // T = 3000ms: second clean tick (streak = 2, but only 2000ms elapsed < 5000ms decreaseCooldownMs)
        syntheticTime = 3000L
        abr.evaluate(AbrTelemetrySignal(newDrops = 0, queueAgeMs = 20, writeLatencyP95Ms = 20))

        // Must NOT probe upward during decrease cooldown!
        assertEquals(4_500_000, abr.currentBitrateBps.value)

        // T = 7000ms (6000ms elapsed > 5000ms decreaseCooldownMs): third clean tick
        syntheticTime = 7000L
        abr.evaluate(AbrTelemetrySignal(newDrops = 0, queueAgeMs = 20, writeLatencyP95Ms = 20))
        // Now upward probing is allowed
        assertTrue(abr.currentBitrateBps.value > 4_500_000)
    }

    @Test
    fun testMaxBitrateCeilingClamping() {
        var syntheticTime = 10_000L

        val abr = AdaptiveBitrateController(
            scope = testScope,
            initialBitrateBps = 9_700_000,
            minBitrateBps = 2_000_000,
            maxBitrateBps = 10_000_000,
            cleanConsecutiveChecksRequired = 1,
            increaseCooldownMs = 1000L,
            decreaseCooldownMs = 1000L,
            timeProvider = { syntheticTime },
            onBitrateAdjusted = {}
        )

        syntheticTime += 2000L
        abr.evaluate(AbrTelemetrySignal(newDrops = 0, queueAgeMs = 10, writeLatencyP95Ms = 10))

        // 9.7M + 500k = 10.2M -> clamped to 10.0M ceiling
        assertEquals(10_000_000, abr.currentBitrateBps.value)
    }

    @Test
    fun testMultiDestinationIsolation_unhealthyDestinationExcludedFromAggregation() {
        // Mock scenario simulating MultiStreamOutputTarget signal calculation:
        // Target 1: YouTube (Healthy: 0 drops, 60ms queue, 45ms write latency)
        // Target 2: Twitch (Unhealthy: 80 drops, 1800ms queue, 600ms write latency)
        val target1IsHealthy = true
        val target2IsHealthy = false // Marked RtmpConnection.State.Unhealthy

        val target1Drops = 0L
        val target1QueueAge = 60L
        val target1Latency = 45L

        val target2Drops = 80L
        val target2QueueAge = 1800L
        val target2Latency = 600L

        // Aggregation algorithm used in MultiStreamOutputTarget:
        // Exclude unhealthy targets from signal computation
        val healthyTargets = listOf(
            Triple(target1IsHealthy, target1Drops, target1QueueAge),
            Triple(target2IsHealthy, target2Drops, target2QueueAge)
        ).filter { it.first }

        val aggregateDrops = healthyTargets.sumOf { it.second }
        val aggregateQueueAge = healthyTargets.maxOfOrNull { it.third } ?: 0L

        assertEquals(0L, aggregateDrops)
        assertEquals(60L, aggregateQueueAge)

        val abr = AdaptiveBitrateController(
            scope = testScope,
            initialBitrateBps = 8_000_000,
            onBitrateAdjusted = {}
        )

        // Controller evaluates aggregate signal from healthy destinations
        abr.evaluate(AbrTelemetrySignal(newDrops = aggregateDrops, queueAgeMs = aggregateQueueAge))

        // Bitrate is NOT dragged down because the unhealthy destination is isolated!
        assertEquals(8_000_000, abr.currentBitrateBps.value)
        assertEquals(RateAdjustmentReason.NONE, abr.lastAdjustmentReason.value)
    }
}

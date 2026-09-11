package pixl.rec.core.engine

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import pixl.rec.core.model.AbrTelemetrySignal
import pixl.rec.core.model.RateAdjustmentReason
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Health telemetry state of the streaming network uplink.
 */
enum class UplinkHealth {
    /** Optimal network conditions, zero packet drops, clean buffer queues. */
    CLEAN,
    /** Transient network congestion detected, rate adaptation in progress. */
    ADAPTING,
    /** Severe packet loss or buffer saturation, throttled to minimum bitrate. */
    CONGESTED
}

/**
 * Adaptive Bitrate Controller (ABR) for RTMP Live Streaming.
 * Continuously evaluates socket buffer queues, write latency percentiles, queue age,
 * and packet drops, dynamically adjusting hardware [VideoEncoder] bitrates via
 * MediaCodec runtime parameters with hysteresis and cooldown guards.
 */
class AdaptiveBitrateController(
    private val scope: CoroutineScope,
    val initialBitrateBps: Int,
    val minBitrateBps: Int = 2_000_000,
    val maxBitrateBps: Int = 14_000_000,
    val decreaseCooldownMs: Long = 3000L,
    val increaseCooldownMs: Long = 5000L,
    val cleanConsecutiveChecksRequired: Int = 5,
    val timeProvider: () -> Long = { System.currentTimeMillis() },
    private val onBitrateAdjusted: (newBitrateBps: Int) -> Unit,
    private val onRequestSyncFrame: () -> Unit = {}
) {
    private val tag = "ABRController"

    private val _currentBitrateBps = MutableStateFlow(initialBitrateBps)
    val currentBitrateBps: StateFlow<Int> = _currentBitrateBps.asStateFlow()

    private val _uplinkHealth = MutableStateFlow(UplinkHealth.CLEAN)
    val uplinkHealth: StateFlow<UplinkHealth> = _uplinkHealth.asStateFlow()

    private val _lastAdjustmentReason = MutableStateFlow(RateAdjustmentReason.NONE)
    val lastAdjustmentReason: StateFlow<RateAdjustmentReason> = _lastAdjustmentReason.asStateFlow()

    private val lastEvaluatedDropCount = AtomicLong(0L)
    private val consecutiveCleanSeconds = AtomicInteger(0)
    private var lastDecreaseTimestamp = 0L
    private var lastIncreaseTimestamp = 0L
    private val isRunning = AtomicBoolean(false)
    private var tickerJob: Job? = null

    /**
     * Starts the 1-second periodic ABR evaluation loop using a multi-signal telemetry provider.
     */
    fun startWithSignal(signalProvider: () -> AbrTelemetrySignal) {
        if (isRunning.getAndSet(true)) return

        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (isActive && isRunning.get()) {
                delay(1000L) // 1Hz ABR evaluation
                val signal = signalProvider()
                evaluate(signal)
            }
        }
    }

    /**
     * Legacy/convenience starter evaluating total dropped frame count.
     */
    fun start(currentTotalDroppedFramesProvider: () -> Long) {
        startWithSignal {
            val totalDropped = currentTotalDroppedFramesProvider()
            val prevDropped = lastEvaluatedDropCount.getAndSet(totalDropped)
            val newDrops = (totalDropped - prevDropped).coerceAtLeast(0L)
            AbrTelemetrySignal(newDrops = newDrops)
        }
    }

    /**
     * Evaluates multi-signal network health and adjusts bitrate using measured AIMD with hysteresis.
     */
    fun evaluate(signal: AbrTelemetrySignal) {
        val current = _currentBitrateBps.value
        val now = timeProvider()
        val timeSinceLastDecrease = now - lastDecreaseTimestamp
        val timeSinceLastIncrease = now - lastIncreaseTimestamp

        // 1. Hard Signal: Packet drops detected (queue overflow or latency budget eviction)
        if (signal.newDrops > 0) {
            consecutiveCleanSeconds.set(0)
            // Allow decrease on hard drops even if in decreaseCooldown, provided at least 1000ms has elapsed,
            // to defensively protect against severe sustained packet loss.
            val canDecrease = timeSinceLastDecrease >= 1000L || lastDecreaseTimestamp == 0L
            if (canDecrease) {
                val reduced = (current * 0.75).toInt().coerceAtLeast(minBitrateBps)
                _uplinkHealth.value = if (reduced <= minBitrateBps) UplinkHealth.CONGESTED else UplinkHealth.ADAPTING
                if (reduced != current) {
                    Log.w(tag, "Hard packet drops (${signal.newDrops}). Stepping down: ${current / 1000}k -> ${reduced / 1000}k")
                    applyBitrateChange(reduced, RateAdjustmentReason.PACKET_DROPS, now, isDecrease = true)
                    onRequestSyncFrame()
                }
            }
            return
        }

        // 2. Early Warning: Oldest packet age in queue exceeds warning threshold (>= 400ms)
        if (signal.queueAgeMs >= 400L) {
            consecutiveCleanSeconds.set(0)
            if (timeSinceLastDecrease >= decreaseCooldownMs || lastDecreaseTimestamp == 0L) {
                val reduced = (current * 0.85).toInt().coerceAtLeast(minBitrateBps)
                _uplinkHealth.value = if (reduced <= minBitrateBps) UplinkHealth.CONGESTED else UplinkHealth.ADAPTING
                if (reduced != current) {
                    Log.w(tag, "Queue latency warning (${signal.queueAgeMs}ms). Stepping down: ${current / 1000}k -> ${reduced / 1000}k")
                    applyBitrateChange(reduced, RateAdjustmentReason.HIGH_QUEUE_AGE, now, isDecrease = true)
                    onRequestSyncFrame()
                }
            }
            return
        }

        // 3. Early Warning: Socket write latency P95 exceeds warning threshold (>= 250ms)
        if (signal.writeLatencyP95Ms >= 250L) {
            consecutiveCleanSeconds.set(0)
            if (timeSinceLastDecrease >= decreaseCooldownMs || lastDecreaseTimestamp == 0L) {
                val reduced = (current * 0.85).toInt().coerceAtLeast(minBitrateBps)
                _uplinkHealth.value = if (reduced <= minBitrateBps) UplinkHealth.CONGESTED else UplinkHealth.ADAPTING
                if (reduced != current) {
                    Log.w(tag, "High write latency P95 (${signal.writeLatencyP95Ms}ms). Stepping down: ${current / 1000}k -> ${reduced / 1000}k")
                    applyBitrateChange(reduced, RateAdjustmentReason.HIGH_WRITE_LATENCY, now, isDecrease = true)
                }
            }
            return
        }

        // 4. Early Warning: Bytes in queue buffer saturation (>= 1.5MB)
        if (signal.bytesInQueue >= 1_500_000L) {
            consecutiveCleanSeconds.set(0)
            if (timeSinceLastDecrease >= decreaseCooldownMs || lastDecreaseTimestamp == 0L) {
                val reduced = (current * 0.85).toInt().coerceAtLeast(minBitrateBps)
                _uplinkHealth.value = if (reduced <= minBitrateBps) UplinkHealth.CONGESTED else UplinkHealth.ADAPTING
                if (reduced != current) {
                    Log.w(tag, "Queue buffer saturation (${signal.bytesInQueue} bytes). Stepping down: ${current / 1000}k -> ${reduced / 1000}k")
                    applyBitrateChange(reduced, RateAdjustmentReason.BUFFER_SATURATION, now, isDecrease = true)
                }
            }
            return
        }

        // 5. Destination Reconnecting Warning
        if (signal.isAnyHealthyDestinationReconnecting) {
            consecutiveCleanSeconds.set(0)
            if (timeSinceLastDecrease >= decreaseCooldownMs || lastDecreaseTimestamp == 0L) {
                val reduced = (current * 0.80).toInt().coerceAtLeast(minBitrateBps)
                _uplinkHealth.value = UplinkHealth.ADAPTING
                if (reduced != current) {
                    Log.w(tag, "Destination reconnecting. Throttling bitrate: ${current / 1000}k -> ${reduced / 1000}k")
                    applyBitrateChange(reduced, RateAdjustmentReason.RECONNECT_DRAIN, now, isDecrease = true)
                }
            }
            return
        }

        // 6. Clean Network Path
        val isClean = signal.newDrops == 0L &&
            signal.queueAgeMs < 150L &&
            signal.writeLatencyP95Ms < 100L &&
            signal.bytesInQueue < 500_000L &&
            !signal.isAnyHealthyDestinationReconnecting

        if (isClean) {
            val cleanSec = consecutiveCleanSeconds.incrementAndGet()

            if (cleanSec >= 2 && _uplinkHealth.value != UplinkHealth.CLEAN) {
                _uplinkHealth.value = UplinkHealth.CLEAN
            }

            // Upward probe requires sustained clean streak AND elapsed cooldowns
            val canProbeUp = cleanSec >= cleanConsecutiveChecksRequired &&
                (timeSinceLastDecrease >= decreaseCooldownMs || lastDecreaseTimestamp == 0L) &&
                (timeSinceLastIncrease >= increaseCooldownMs || lastIncreaseTimestamp == 0L)

            if (canProbeUp && current < maxBitrateBps) {
                // Additive increase: +8% or minimum 500kbps, clamped to maxBitrateBps
                val step = Math.max(500_000, (current * 0.08).toInt())
                val increased = (current + step).coerceAtMost(maxBitrateBps)
                Log.i(tag, "Uplink stable for ${cleanSec}s. Probing upward: ${current / 1000}k -> ${increased / 1000}k")
                applyBitrateChange(increased, RateAdjustmentReason.CONGESTION_AVOIDANCE_PROBE, now, isDecrease = false)
                consecutiveCleanSeconds.set(0)
            }
        } else {
            // Borderline conditions: neither congested enough to step down nor clean enough to probe upward
            consecutiveCleanSeconds.set(0)
        }
    }

    /**
     * Backwards-compatible evaluation entrypoint.
     */
    internal fun evaluateBitrate(newDrops: Long) {
        evaluate(AbrTelemetrySignal(newDrops = newDrops))
    }

    private fun applyBitrateChange(
        newBitrate: Int,
        reason: RateAdjustmentReason,
        timestamp: Long,
        isDecrease: Boolean
    ) {
        _currentBitrateBps.value = newBitrate
        _lastAdjustmentReason.value = reason
        if (isDecrease) {
            lastDecreaseTimestamp = timestamp
        } else {
            lastIncreaseTimestamp = timestamp
        }
        onBitrateAdjusted(newBitrate)
    }

    /**
     * Stops the ABR controller.
     */
    fun stop() {
        isRunning.set(false)
        tickerJob?.cancel()
        tickerJob = null
    }

    /**
     * Resets controller state to initial configuration.
     */
    fun reset() {
        _currentBitrateBps.value = initialBitrateBps
        _uplinkHealth.value = UplinkHealth.CLEAN
        _lastAdjustmentReason.value = RateAdjustmentReason.NONE
        lastEvaluatedDropCount.set(0L)
        consecutiveCleanSeconds.set(0)
        lastDecreaseTimestamp = 0L
        lastIncreaseTimestamp = 0L
    }
}

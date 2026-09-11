package pixl.rec.core.model

import java.util.Arrays
import java.util.concurrent.atomic.AtomicInteger

/**
 * High-granularity classification of media packets in the streaming pipeline.
 * Enables selective frame dropping, priority queuing, and accurate drop accounting.
 */
enum class PacketMediaType {
    VIDEO_KEYFRAME,
    VIDEO_INTER,
    VIDEO_SEQUENCE_HEADER,
    AUDIO,
    AUDIO_SEQUENCE_HEADER,
    METADATA_COMMAND
}

/**
 * Root-cause classification for dropped stream packets.
 */
enum class DropReason {
    QUEUE_OVERFLOW,
    SOCKET_TIMEOUT,
    CONNECTION_CLOSED,
    LATENCY_BUDGET_EXCEEDED
}

/**
 * Non-allocating thread-safe circular reservoir for tracking socket write durations (ms)
 * and computing rolling p50, p95, and p99 percentiles without garbage collection overhead.
 */
class WriteLatencyMetrics(val capacity: Int = 128) {
    private val buffer = LongArray(capacity)
    private val head = AtomicInteger(0)
    private val totalRecorded = AtomicInteger(0)

    fun record(latencyMs: Long) {
        val idx = Math.floorMod(head.getAndIncrement(), capacity)
        buffer[idx] = latencyMs.coerceAtLeast(0L)
        totalRecorded.incrementAndGet()
    }

    /**
     * Computes the p50, p95, and p99 latency values in milliseconds.
     * Returns Triple(p50, p95, p99).
     */
    fun getPercentiles(): Triple<Long, Long, Long> {
        val count = totalRecorded.get().coerceAtMost(capacity)
        if (count == 0) return Triple(0L, 0L, 0L)

        // Snapshot existing entries into a compact stack array to calculate percentiles
        val copy = LongArray(count)
        val currentHead = head.get()
        val startIdx = if (totalRecorded.get() > capacity) Math.floorMod(currentHead, capacity) else 0

        for (i in 0 until count) {
            copy[i] = buffer[(startIdx + i) % capacity]
        }
        Arrays.sort(copy)

        val p50 = copy[(count * 0.50).toInt().coerceAtMost(count - 1)]
        val p95 = copy[(count * 0.95).toInt().coerceAtMost(count - 1)]
        val p99 = copy[(count * 0.99).toInt().coerceAtMost(count - 1)]

        return Triple(p50, p95, p99)
    }

    fun reset() {
        head.set(0)
        totalRecorded.set(0)
    }
}

/**
 * Comprehensive telemetry metrics for an individual RTMP stream target destination.
 */
data class DestinationTelemetry(
    val destinationId: String = "",
    val platformName: String = "",
    val endpointUrl: String = "",
    val isConnected: Boolean = false,
    val bytesTransmitted: Long = 0L,
    val bytesInQueue: Long = 0L,
    val queueDepthPackets: Int = 0,
    val oldestPacketAgeMs: Long = 0L,
    val timeAboveLatencyBudgetMs: Long = 0L,
    val writeLatencyP50Ms: Long = 0L,
    val writeLatencyP95Ms: Long = 0L,
    val writeLatencyP99Ms: Long = 0L,
    val droppedAudio: Long = 0L,
    val droppedVideoKey: Long = 0L,
    val droppedVideoInter: Long = 0L,
    val droppedHeaders: Long = 0L,
    val reconnectCount: Int = 0,
    val lastReconnectDurationMs: Long = 0L,
    val timeToFirstKeyframeMs: Long = 0L
) {
    val totalDroppedPackets: Long
        get() = droppedAudio + droppedVideoKey + droppedVideoInter + droppedHeaders
}

/**
 * Aggregate packet drop totals across all destinations.
 */
data class AggregateDrops(
    val totalDrops: Long = 0L,
    val droppedAudio: Long = 0L,
    val droppedVideoKey: Long = 0L,
    val droppedVideoInter: Long = 0L,
    val droppedHeaders: Long = 0L
)

/**
 * Root-cause classification for dynamic ABR bitrate adjustments.
 */
enum class RateAdjustmentReason {
    NONE,
    PACKET_DROPS,
    HIGH_QUEUE_AGE,
    HIGH_WRITE_LATENCY,
    BUFFER_SATURATION,
    RECONNECT_DRAIN,
    CONGESTION_AVOIDANCE_PROBE
}

/**
 * Multi-signal input telemetry evaluated by the Adaptive Bitrate Controller.
 */
data class AbrTelemetrySignal(
    val newDrops: Long = 0L,
    val queueAgeMs: Long = 0L,
    val writeLatencyP95Ms: Long = 0L,
    val bytesInQueue: Long = 0L,
    val isAnyHealthyDestinationReconnecting: Boolean = false
)

/**
 * Pre-session or idle device baseline snapshot.
 * All subsequent thermal, power, memory, and performance metrics are measured
 * differentially relative to this baseline rather than against unsupported universal thresholds.
 */
data class DeviceBaselineSnapshot(
    val timestampNs: Long = System.nanoTime(),
    val batteryLevelPercent: Float = 0f,
    val batteryTempCelsius: Float = 0f,
    val batteryCurrentNowUa: Long = 0L,
    val thermalStatus: Int = 0, // android.os.PowerManager.THERMAL_STATUS_NONE
    val pssMemoryKb: Long = 0L,
    val gameFps: Float = 0f
)

/**
 * High-precision differential telemetry snapshot computed relative to [DeviceBaselineSnapshot].
 * Complies with Section 10 & 12 of the Measured Live Streaming Blueprint.
 */
data class DifferentialTelemetrySnapshot(
    val baseline: DeviceBaselineSnapshot = DeviceBaselineSnapshot(),
    val elapsedDurationMs: Long = 0L,
    val currentBatteryTempCelsius: Float = 0f,
    val deltaBatteryTempCelsius: Float = 0f, // (current - baseline)
    val batteryDrainRatePercentPerHour: Float = 0f,
    val batteryCurrentNowMa: Float = 0f,
    val currentThermalStatus: Int = 0,
    val thermalThrottlingEvents: Int = 0, // Counts transitions into SEVERE or CRITICAL
    val currentPssMemoryKb: Long = 0L,
    val memorySlopeKbPerHour: Double = 0.0, // Linear regression slope (kB/hr)
    val encoderFps: Float = 0f,
    val gameFps: Float = 0f,
    val deltaGameFps: Float = 0f, // (current - baseline)
    val activeDestinationsCount: Int = 0,
    val socketWriteLatencyP95Ms: Long = 0L,
    val totalThroughputBps: Long = 0L
)

/**
 * Session-wide telemetry aggregated across the media pipeline and all streaming endpoints.
 */
data class SessionStreamTelemetry(
    val totalBytesEncoded: Long = 0L,
    val totalBytesPacketized: Long = 0L,
    val totalThroughputBps: Long = 0L,
    val encoderOutputFps: Float = 0f,
    val avgPacketizationTimeUs: Long = 0L,
    val audioVideoPtsDriftMs: Long = 0L,
    val destinations: Map<String, DestinationTelemetry> = emptyMap(),
    val currentBitrateBps: Int = 0,
    val lastAdjustmentReason: RateAdjustmentReason = RateAdjustmentReason.NONE,
    val differentialTelemetry: DifferentialTelemetrySnapshot? = null
) {
    val totalBytesTransmitted: Long
        get() = destinations.values.sumOf { it.bytesTransmitted }

    val totalPacketsDropped: Long
        get() = destinations.values.sumOf { it.totalDroppedPackets }

    val aggregateDrops: AggregateDrops
        get() = AggregateDrops(
            totalDrops = totalPacketsDropped,
            droppedAudio = destinations.values.sumOf { it.droppedAudio },
            droppedVideoKey = destinations.values.sumOf { it.droppedVideoKey },
            droppedVideoInter = destinations.values.sumOf { it.droppedVideoInter },
            droppedHeaders = destinations.values.sumOf { it.droppedHeaders }
        )

    val maxQueueAgeMs: Long
        get() = destinations.values.maxOfOrNull { it.oldestPacketAgeMs } ?: 0L
}


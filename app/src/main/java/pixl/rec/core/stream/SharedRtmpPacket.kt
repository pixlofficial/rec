package pixl.rec.core.stream

import java.util.concurrent.atomic.AtomicInteger

/**
 * Thread-safe reference-counted container wrapping an immutable [RtmpPacket].
 *
 * Used in multi-destination broadcasting (Multistreaming) to eliminate redundant
 * packetization and payload copying across child destinations.
 *
 * @property packet The immutable encapsulated RTMP protocol packet.
 * @property initialReferences Number of destination queues receiving this packet reference.
 * @property onReclaimed Optional callback invoked when all destinations have released or dropped their reference.
 */
class SharedRtmpPacket(
    val packet: RtmpPacket,
    initialReferences: Int,
    private val onReclaimed: ((RtmpPacket) -> Unit)? = null
) {
    private val refCount = AtomicInteger(initialReferences)

    /**
     * Current active references remaining for this packet.
     */
    val activeReferences: Int
        get() = refCount.get()

    /**
     * Retains this packet, incrementing the reference count.
     */
    fun retain(): SharedRtmpPacket {
        refCount.incrementAndGet()
        return this
    }

    /**
     * Releases one reference to this packet.
     * When reference count drops to 0, invokes [onReclaimed] for buffer/memory reclamation.
     *
     * @return The updated reference count after decrement.
     */
    fun release(): Int {
        val remaining = refCount.decrementAndGet()
        if (remaining == 0) {
            onReclaimed?.invoke(packet)
        }
        return remaining
    }

    /**
     * Whether all references have been released.
     */
    val isReleased: Boolean
        get() = refCount.get() <= 0
}

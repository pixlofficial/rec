package pixl.rec.core.replay

import android.media.MediaFormat

/**
 * Immutable snapshot extracted from the [ReplayRingBuffer] upon tactical clip trigger.
 * Contains normalized presentation timestamps (PTS_0 = 0) guaranteed to start on a keyframe.
 */
data class ReplaySnapshot(
    val videoFormat: MediaFormat,
    val audioFormat: MediaFormat?,
    val samples: List<ReplaySample>,
    val durationMs: Long,
    val totalBytes: Long
)

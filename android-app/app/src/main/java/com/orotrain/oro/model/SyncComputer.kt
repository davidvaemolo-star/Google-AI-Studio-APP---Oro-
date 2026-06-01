package com.orotrain.oro.model

import kotlin.math.abs
import kotlin.math.min

/**
 * One detected Catch, recorded during a Session for end-of-session Sync Score (ADR-0015).
 *
 * - [deviceTimestampMs] is the firmware's own millis() at the Catch — used (after clock alignment)
 *   to compare devices precisely, free of per-message Bluetooth jitter.
 * - [phoneReceiveMs] is when the phone received the notification — used only to estimate each
 *   device's clock offset.
 */
data class CatchSample(
    val deviceId: String,
    val deviceTimestampMs: Long,
    val phoneReceiveMs: Long
)

/**
 * Computes crew synchronisation from the Catches recorded during a Session (ADR-0015).
 *
 * Each device's clock is aligned into a common timeline using the *fastest* observed message
 * (the minimum of `phoneReceiveMs - deviceTimestampMs`), which strips out Bluetooth jitter and a
 * device's average-latency bias. Each follower Catch is then paired to the nearest pacer Catch
 * within [MATCH_WINDOW_MS]; the score is the *absolute* gap, so rushing ahead of the pacer counts
 * against sync exactly as much as lagging behind.
 */
object SyncComputer {
    /** A follower Catch with no pacer Catch within this window counts as a maximum-gap stroke. */
    const val MATCH_WINDOW_MS = 400L
    /** Gap is capped here; the scoring scale already hits 0 at 300 ms. */
    const val MAX_GAP_MS = 300.0

    /**
     * The crew's average absolute catch gap in ms, or null when there is nothing to measure
     * (no pacer Catches, or no follower Catches) — i.e. the Sync Score is "Not Measured".
     */
    fun crewAverageGapMs(samples: List<CatchSample>, pacerId: String?): Double? {
        if (pacerId == null || samples.isEmpty()) return null

        // Per-device clock offset: the smallest (phoneReceive - deviceTimestamp) seen. The fastest
        // delivery has the least Bluetooth delay, so its offset is closest to the true one.
        val offsets = samples.groupBy { it.deviceId }
            .mapValues { (_, list) -> list.minOf { it.phoneReceiveMs - it.deviceTimestampMs } }

        fun aligned(s: CatchSample): Long = s.deviceTimestampMs + offsets.getValue(s.deviceId)

        val pacerAligned = samples.filter { it.deviceId == pacerId }.map(::aligned).sorted()
        if (pacerAligned.isEmpty()) return null

        val followerCatches = samples.filter { it.deviceId != pacerId }
        if (followerCatches.isEmpty()) return null

        val gaps = followerCatches.map { f ->
            val distance = nearestDistanceMs(pacerAligned, aligned(f))
            if (distance > MATCH_WINDOW_MS) MAX_GAP_MS else min(distance.toDouble(), MAX_GAP_MS)
        }
        return gaps.average()
    }

    /** Smallest absolute distance from [t] to any value in the ascending-sorted [sorted]. */
    private fun nearestDistanceMs(sorted: List<Long>, t: Long): Long {
        val found = sorted.binarySearch(t)
        if (found >= 0) return 0L
        val insertion = -found - 1
        val before = if (insertion - 1 in sorted.indices) abs(t - sorted[insertion - 1]) else Long.MAX_VALUE
        val after = if (insertion in sorted.indices) abs(t - sorted[insertion]) else Long.MAX_VALUE
        return min(before, after)
    }
}

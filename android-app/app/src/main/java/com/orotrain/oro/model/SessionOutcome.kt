package com.orotrain.oro.model

/**
 * Computed crew-wide result at the end of a Session. See CONTEXT.md "Session Outcome".
 * Pure data — knows nothing about audio prompts or screens.
 */
data class SessionOutcome(
    val syncScore: Int,
    val syncRating: SyncRating,
    val powerRange: PowerRange
) {
    companion object {
        // Sync Score is linear: 50ms avg latency → 100, 300ms avg latency → 0.
        // See CONTEXT.md "Sync Score".
        private const val PERFECT_SYNC_LATENCY_MS = 50.0
        private const val ZERO_SYNC_LATENCY_MS    = 300.0
        private const val SYNC_RANGE_MS           = ZERO_SYNC_LATENCY_MS - PERFECT_SYNC_LATENCY_MS

        fun compute(
            followerLatenciesMs: Collection<Int>,
            strokeFsrPeakPercents: Collection<Int>
        ): SessionOutcome {
            val avgLatencyMs = if (followerLatenciesMs.isEmpty()) ZERO_SYNC_LATENCY_MS
                               else followerLatenciesMs.average()
            val syncScore = ((ZERO_SYNC_LATENCY_MS - avgLatencyMs) / SYNC_RANGE_MS * 100.0)
                .coerceIn(0.0, 100.0).toInt()
            val avgFsrPeak = if (strokeFsrPeakPercents.isEmpty()) 0
                             else strokeFsrPeakPercents.average().toInt()
            return SessionOutcome(
                syncScore = syncScore,
                syncRating = SyncRating.fromScore(syncScore),
                powerRange = PowerRange.fromPeakPercent(avgFsrPeak)
            )
        }
    }
}

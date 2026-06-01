package com.orotrain.oro.model

/**
 * Computed crew-wide result at the end of a Session. See CONTEXT.md "Session Outcome".
 * Pure data — knows nothing about audio prompts or screens.
 */
data class SessionOutcome(
    // syncScore/syncRating are null when sync was Not Measured (no follower Catches to compare —
    // e.g. a single-device session). This is distinct from a Poor score. (ADR-0015)
    val syncScore: Int?,
    val syncRating: SyncRating?,
    val powerRange: PowerRange
) {
    val syncMeasured: Boolean get() = syncScore != null

    companion object {
        // Sync Score is linear on the absolute catch gap: ≤50ms → 100, ≥300ms → 0.
        // See CONTEXT.md "Sync Score" and ADR-0015.
        private const val PERFECT_SYNC_GAP_MS = 50.0
        private const val ZERO_SYNC_GAP_MS    = 300.0
        private const val SYNC_RANGE_MS       = ZERO_SYNC_GAP_MS - PERFECT_SYNC_GAP_MS

        /** Converts an absolute catch gap (ms) to a 0–100 Sync Score, or null when Not Measured. */
        fun scoreForGap(gapMs: Double?): Int? = gapMs?.let { gap ->
            ((ZERO_SYNC_GAP_MS - gap) / SYNC_RANGE_MS * 100.0).coerceIn(0.0, 100.0).toInt()
        }

        /**
         * @param crewAverageGapMs the crew's average absolute follower↔pacer catch gap, or null
         *        when there was nothing to measure (Sync Score = Not Measured).
         */
        fun compute(
            crewAverageGapMs: Double?,
            strokeFsrPeakPercents: Collection<Int>
        ): SessionOutcome {
            val syncScore = scoreForGap(crewAverageGapMs)
            val avgFsrPeak = if (strokeFsrPeakPercents.isEmpty()) 0
                             else strokeFsrPeakPercents.average().toInt()
            return SessionOutcome(
                syncScore = syncScore,
                syncRating = syncScore?.let { SyncRating.fromScore(it) },
                powerRange = PowerRange.fromPeakPercent(avgFsrPeak)
            )
        }
    }
}

package com.orotrain.oro.model

import org.junit.Assert.assertEquals
import org.junit.Test

class SessionOutcomeTest {

    // --- SyncRating brackets ---

    @Test fun `sync rating excellent at 80 and above`() {
        assertEquals(SyncRating.Excellent, SyncRating.fromScore(80))
        assertEquals(SyncRating.Excellent, SyncRating.fromScore(100))
    }

    @Test fun `sync rating good between 50 and 79`() {
        assertEquals(SyncRating.Good, SyncRating.fromScore(50))
        assertEquals(SyncRating.Good, SyncRating.fromScore(79))
    }

    @Test fun `sync rating poor below 50`() {
        assertEquals(SyncRating.Poor, SyncRating.fromScore(0))
        assertEquals(SyncRating.Poor, SyncRating.fromScore(49))
    }

    // --- PowerRange brackets ---

    @Test fun `power range light below 26`() {
        assertEquals(PowerRange.Light, PowerRange.fromPeakPercent(0))
        assertEquals(PowerRange.Light, PowerRange.fromPeakPercent(25))
    }

    @Test fun `power range moderate 26 to 50`() {
        assertEquals(PowerRange.Moderate, PowerRange.fromPeakPercent(26))
        assertEquals(PowerRange.Moderate, PowerRange.fromPeakPercent(50))
    }

    @Test fun `power range strong 51 to 75`() {
        assertEquals(PowerRange.Strong, PowerRange.fromPeakPercent(51))
        assertEquals(PowerRange.Strong, PowerRange.fromPeakPercent(75))
    }

    @Test fun `power range maximum 76 and above`() {
        assertEquals(PowerRange.Maximum, PowerRange.fromPeakPercent(76))
        assertEquals(PowerRange.Maximum, PowerRange.fromPeakPercent(100))
    }

    // --- SessionOutcome.compute: Sync Score formula ---

    @Test fun `perfect sync 50ms average yields score 100 and excellent`() {
        val outcome = SessionOutcome.compute(
            followerLatenciesMs = listOf(50, 50, 50),
            strokeFsrPeakPercents = listOf(60)
        )
        assertEquals(100, outcome.syncScore)
        assertEquals(SyncRating.Excellent, outcome.syncRating)
    }

    @Test fun `zero sync 300ms average yields score 0 and poor`() {
        val outcome = SessionOutcome.compute(
            followerLatenciesMs = listOf(300, 300),
            strokeFsrPeakPercents = listOf(40)
        )
        assertEquals(0, outcome.syncScore)
        assertEquals(SyncRating.Poor, outcome.syncRating)
    }

    @Test fun `mid latency 175ms yields score 50 and good`() {
        val outcome = SessionOutcome.compute(
            followerLatenciesMs = listOf(175),
            strokeFsrPeakPercents = listOf(40)
        )
        assertEquals(50, outcome.syncScore)
        assertEquals(SyncRating.Good, outcome.syncRating)
    }

    @Test fun `latency below 50ms is clamped to score 100`() {
        val outcome = SessionOutcome.compute(
            followerLatenciesMs = listOf(0, 10),
            strokeFsrPeakPercents = listOf(0)
        )
        assertEquals(100, outcome.syncScore)
    }

    @Test fun `latency above 300ms is clamped to score 0`() {
        val outcome = SessionOutcome.compute(
            followerLatenciesMs = listOf(500, 1000),
            strokeFsrPeakPercents = listOf(0)
        )
        assertEquals(0, outcome.syncScore)
    }

    // --- SessionOutcome.compute: empty inputs ---

    @Test fun `no follower latencies treated as worst case`() {
        val outcome = SessionOutcome.compute(
            followerLatenciesMs = emptyList(),
            strokeFsrPeakPercents = listOf(50)
        )
        assertEquals(0, outcome.syncScore)
        assertEquals(SyncRating.Poor, outcome.syncRating)
    }

    @Test fun `no strokes yields light power range`() {
        val outcome = SessionOutcome.compute(
            followerLatenciesMs = listOf(50),
            strokeFsrPeakPercents = emptyList()
        )
        assertEquals(PowerRange.Light, outcome.powerRange)
    }

    // --- SessionOutcome.compute: power range derived from average ---

    @Test fun `power range uses average peak across all strokes`() {
        val outcome = SessionOutcome.compute(
            followerLatenciesMs = listOf(100),
            strokeFsrPeakPercents = listOf(60, 80, 100)  // avg 80
        )
        assertEquals(PowerRange.Maximum, outcome.powerRange)
    }
}

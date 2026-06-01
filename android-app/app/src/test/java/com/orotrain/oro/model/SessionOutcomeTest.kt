package com.orotrain.oro.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    // --- SessionOutcome.scoreForGap: shared gap→score conversion, reused per-device (ADR-0016) ---

    @Test fun `scoreForGap maps 50ms to 100`() {
        assertEquals(100, SessionOutcome.scoreForGap(50.0))
    }

    @Test fun `scoreForGap maps 300ms to 0`() {
        assertEquals(0, SessionOutcome.scoreForGap(300.0))
    }

    @Test fun `scoreForGap maps 175ms to 50`() {
        assertEquals(50, SessionOutcome.scoreForGap(175.0))
    }

    @Test fun `scoreForGap clamps below 50ms to 100`() {
        assertEquals(100, SessionOutcome.scoreForGap(5.0))
    }

    @Test fun `scoreForGap clamps above 300ms to 0`() {
        assertEquals(0, SessionOutcome.scoreForGap(750.0))
    }

    @Test fun `scoreForGap of null gap is null`() {
        assertNull(SessionOutcome.scoreForGap(null))
    }

    // --- SessionOutcome.compute: Sync Score formula (absolute crew gap, ADR-0015) ---

    @Test fun `perfect sync 50ms gap yields score 100 and excellent`() {
        val outcome = SessionOutcome.compute(crewAverageGapMs = 50.0, strokeFsrPeakPercents = listOf(60))
        assertEquals(100, outcome.syncScore)
        assertEquals(SyncRating.Excellent, outcome.syncRating)
    }

    @Test fun `zero sync 300ms gap yields score 0 and poor`() {
        val outcome = SessionOutcome.compute(crewAverageGapMs = 300.0, strokeFsrPeakPercents = listOf(40))
        assertEquals(0, outcome.syncScore)
        assertEquals(SyncRating.Poor, outcome.syncRating)
    }

    @Test fun `mid gap 175ms yields score 50 and good`() {
        val outcome = SessionOutcome.compute(crewAverageGapMs = 175.0, strokeFsrPeakPercents = listOf(40))
        assertEquals(50, outcome.syncScore)
        assertEquals(SyncRating.Good, outcome.syncRating)
    }

    @Test fun `gap below 50ms is clamped to score 100`() {
        val outcome = SessionOutcome.compute(crewAverageGapMs = 5.0, strokeFsrPeakPercents = listOf(0))
        assertEquals(100, outcome.syncScore)
    }

    @Test fun `gap above 300ms is clamped to score 0`() {
        val outcome = SessionOutcome.compute(crewAverageGapMs = 750.0, strokeFsrPeakPercents = listOf(0))
        assertEquals(0, outcome.syncScore)
    }

    // --- SessionOutcome.compute: Not Measured (no follower data) ---

    @Test fun `null crew gap is Not Measured, not Poor`() {
        val outcome = SessionOutcome.compute(crewAverageGapMs = null, strokeFsrPeakPercents = listOf(50))
        assertNull(outcome.syncScore)
        assertNull(outcome.syncRating)
        assertEquals(false, outcome.syncMeasured)
    }

    @Test fun `no strokes yields light power range`() {
        val outcome = SessionOutcome.compute(crewAverageGapMs = 50.0, strokeFsrPeakPercents = emptyList())
        assertEquals(PowerRange.Light, outcome.powerRange)
    }

    // --- SessionOutcome.compute: power range derived from average ---

    @Test fun `power range uses average peak across all strokes`() {
        val outcome = SessionOutcome.compute(crewAverageGapMs = 100.0, strokeFsrPeakPercents = listOf(60, 80, 100))  // avg 80
        assertEquals(PowerRange.Maximum, outcome.powerRange)
    }
}

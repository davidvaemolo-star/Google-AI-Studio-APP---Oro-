package com.orotrain.oro.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the end-of-session Crew Roll-Call scoring (ADR-0016): a crew-wide Sync Rating plus one
 * per-seat line carrying that paddler's own Sync Rating and Power Range.
 *
 * Shared scenario: P is the Pacer; F1 catches perfectly in sync; F2 lags 200ms every stroke; F3 is
 * connected but never produced a Catch.
 */
class CrewRollCallTest {

    private val pacerId = "P"
    private val roster = listOf(
        RosterSeat("P", canoe = 0, seat = 1),
        RosterSeat("F1", canoe = 0, seat = 2),
        RosterSeat("F2", canoe = 0, seat = 3),
        RosterSeat("F3", canoe = 0, seat = 4)
    )
    private val catches = listOf(
        CatchSample("P", 1000, 1010), CatchSample("P", 2000, 2010), CatchSample("P", 3000, 3010),
        CatchSample("F1", 1000, 1010), CatchSample("F1", 2000, 2010), CatchSample("F1", 3000, 3010),
        CatchSample("F2", 1200, 1210), CatchSample("F2", 2200, 2210), CatchSample("F2", 3200, 3210)
        // F3: no catches
    )
    private val pressures = mapOf(
        "P" to listOf(80), "F1" to listOf(60), "F2" to listOf(10), "F3" to emptyList()
    )

    private fun rollCall() = CrewRollCall.compute(catches, pacerId, roster, pressures)
    private fun seat(id: String) = rollCall().seats.first { it.deviceId == id }

    @Test fun `crew rating comes from the crew-wide gap`() {
        val rc = rollCall()
        assertEquals(80, rc.crewSyncScore)               // crew avg gap 100ms → 80
        assertEquals(SyncRating.Excellent, rc.crewSyncRating)
    }

    @Test fun `pacer seat has no sync but keeps its power`() {
        val p = seat("P")
        assertTrue(p.isPacer)
        assertNull(p.syncScore)
        assertNull(p.syncRating)
        assertEquals(PowerRange.Maximum, p.powerRange)   // avg 80
    }

    @Test fun `each follower gets its own sync rating`() {
        assertEquals(SyncRating.Excellent, seat("F1").syncRating)  // 0ms gap → 100
        assertEquals(SyncRating.Poor, seat("F2").syncRating)       // 200ms gap → 40
    }

    @Test fun `a follower that never caught is Not Measured`() {
        val f3 = seat("F3")
        assertNull(f3.syncScore)
        assertNull(f3.syncRating)
    }

    @Test fun `power range is per device`() {
        assertEquals(PowerRange.Strong, seat("F1").powerRange)  // avg 60
        assertEquals(PowerRange.Light, seat("F2").powerRange)   // avg 10
        assertEquals(PowerRange.Light, seat("F3").powerRange)   // no pressure data
    }

    @Test fun `seats follow the given read-out order`() {
        assertEquals(listOf("P", "F1", "F2", "F3"), rollCall().seats.map { it.deviceId })
    }

    @Test fun `each seat carries its seat number and canoe`() {
        assertEquals(1, seat("P").seat)
        assertEquals(3, seat("F2").seat)
        assertEquals(0, seat("F2").canoe)
    }

    @Test fun `crew sync is Not Measured when no follower caught`() {
        val rc = CrewRollCall.compute(
            catchSamples = listOf(CatchSample("P", 1000, 1010)),
            pacerId = "P",
            roster = listOf(RosterSeat("P", canoe = 0, seat = 1)),
            devicePressurePercents = mapOf("P" to listOf(40))
        )
        assertNull(rc.crewSyncScore)
        assertNull(rc.crewSyncRating)
        assertEquals(PowerRange.Moderate, rc.seats.single().powerRange)  // avg 40
    }
}

package com.orotrain.oro.ble

import com.orotrain.oro.model.CrewRollCall
import com.orotrain.oro.model.PowerRange
import com.orotrain.oro.model.SeatScore
import com.orotrain.oro.model.SyncRating
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests for the Roll-Call Control wire encoding (BLE_PROTOCOL §1.10, ADR-0016).
 *
 * Fixture: P is the Pacer (Seat 1, Strong power, no sync); F2 in Seat 2 (Excellent sync, Maximum
 * power); F3 in Canoe 1 Seat 3 (sync Not Measured, Light power).
 */
class RollCallCodecTest {

    private val rollCall = CrewRollCall(
        crewSyncScore = 80,
        crewSyncRating = SyncRating.Good,
        seats = listOf(
            SeatScore("P", canoe = 0, seat = 1, isPacer = true,
                syncScore = null, syncRating = null, powerRange = PowerRange.Strong),
            SeatScore("F2", canoe = 0, seat = 2, isPacer = false,
                syncScore = 100, syncRating = SyncRating.Excellent, powerRange = PowerRange.Maximum),
            SeatScore("F3", canoe = 1, seat = 3, isPacer = false,
                syncScore = null, syncRating = null, powerRange = PowerRange.Light)
        )
    )

    // --- LOAD ---

    @Test fun `load header is command, crew rating, seat count`() {
        val bytes = RollCallCodec.encodeLoad(rollCall)
        assertEquals(RollCallCodec.CMD_LOAD, bytes[0])
        assertEquals(2.toByte(), bytes[1])   // crew rating Good = 2
        assertEquals(3.toByte(), bytes[2])   // 3 seats
    }

    @Test fun `load pacer entry sets the pacer bit and a Not-Measured sync nibble`() {
        val bytes = RollCallCodec.encodeLoad(rollCall)
        // Entry 0 begins at byte 3: canoe, seat|pacerBit, score(power<<4 | sync)
        assertEquals(0.toByte(), bytes[3])           // canoe 0 (single)
        assertEquals(0x81.toByte(), bytes[4])        // seat 1 | pacer bit 0x80
        assertEquals(0x30.toByte(), bytes[5])        // Strong(3)<<4 | NotMeasured(0)
    }

    @Test fun `load follower entry packs sync and power nibbles`() {
        val bytes = RollCallCodec.encodeLoad(rollCall)
        // Entry 1 begins at byte 6
        assertEquals(0.toByte(), bytes[6])           // canoe 0
        assertEquals(2.toByte(), bytes[7])           // seat 2, not pacer
        assertEquals(0x43.toByte(), bytes[8])        // Maximum(4)<<4 | Excellent(3)
    }

    @Test fun `load not-measured follower has a zero sync nibble and its canoe`() {
        val bytes = RollCallCodec.encodeLoad(rollCall)
        // Entry 2 begins at byte 9
        assertEquals(1.toByte(), bytes[9])           // canoe 1
        assertEquals(3.toByte(), bytes[10])          // seat 3
        assertEquals(0x10.toByte(), bytes[11])       // Light(1)<<4 | NotMeasured(0)
    }

    @Test fun `load length is three plus three per seat`() {
        assertEquals(3 + 3 * 3, RollCallCodec.encodeLoad(rollCall).size)
    }

    @Test fun `load crew rating is Not Measured when null`() {
        val bytes = RollCallCodec.encodeLoad(rollCall.copy(crewSyncRating = null))
        assertEquals(0.toByte(), bytes[1])
    }

    // --- PLAY ---

    @Test fun `play packs command, delay in tens of ms, and volume`() {
        val bytes = RollCallCodec.encodePlay(startDelayMs = 300, volume = 90)
        assertEquals(RollCallCodec.CMD_PLAY, bytes[0])
        assertEquals(30.toByte(), bytes[1])   // 300ms / 10
        assertEquals(90.toByte(), bytes[2])
    }

    @Test fun `play clamps an over-long delay and an over-loud volume`() {
        val bytes = RollCallCodec.encodePlay(startDelayMs = 5000, volume = 150)
        assertEquals(255.toByte(), bytes[1])  // 500 units clamped to 255
        assertEquals(100.toByte(), bytes[2])
    }

    @Test fun `play floors a negative delay to zero`() {
        val bytes = RollCallCodec.encodePlay(startDelayMs = -100, volume = 50)
        assertEquals(0.toByte(), bytes[1])
    }
}

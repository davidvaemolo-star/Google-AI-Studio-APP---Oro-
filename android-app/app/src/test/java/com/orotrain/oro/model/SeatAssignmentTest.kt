package com.orotrain.oro.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SeatAssignmentTest {

    private fun device(id: String, seat: Int? = null, name: String = "Oro-0$id") =
        HapticDevice(
            id = id,
            name = name,
            status = DeviceStatus.Connected,
            seat = seat
        )

    // --- swapSeats ---

    @Test
    fun `swapSeats assigns seat to unoccupied slot`() {
        val devices = listOf(device("a", seat = 1), device("b", seat = 2))
        val result = swapSeats(devices, deviceId = "b", newSeat = 3)
        assertEquals(3, result.find { it.id == "b" }?.seat)
        assertEquals(1, result.find { it.id == "a" }?.seat)
    }

    @Test
    fun `swapSeats swaps seats when target seat is occupied`() {
        val devices = listOf(device("a", seat = 1), device("b", seat = 2))
        val result = swapSeats(devices, deviceId = "b", newSeat = 1)
        assertEquals(1, result.find { it.id == "b" }?.seat)
        assertEquals(2, result.find { it.id == "a" }?.seat)
    }

    @Test
    fun `swapSeats returns unchanged list when deviceId not found`() {
        val devices = listOf(device("a", seat = 1))
        val result = swapSeats(devices, deviceId = "z", newSeat = 2)
        assertEquals(devices, result)
    }

    @Test
    fun `swapSeats no-ops when device already in target seat`() {
        val devices = listOf(device("a", seat = 1), device("b", seat = 2))
        val result = swapSeats(devices, deviceId = "a", newSeat = 1)
        assertEquals(1, result.find { it.id == "a" }?.seat)
        assertEquals(2, result.find { it.id == "b" }?.seat)
    }

    // --- autoAssignSeats ---

    @Test
    fun `autoAssignSeats fills seats for unassigned devices in name order`() {
        val devices = listOf(
            device("b", seat = null, name = "Oro-02"),
            device("a", seat = null, name = "Oro-01")
        )
        val result = autoAssignSeats(devices)
        assertEquals(1, result.find { it.id == "a" }?.seat)
        assertEquals(2, result.find { it.id == "b" }?.seat)
    }

    @Test
    fun `autoAssignSeats preserves existing seat assignments`() {
        val devices = listOf(
            device("a", seat = 3),
            device("b", seat = null, name = "Oro-01")
        )
        val result = autoAssignSeats(devices)
        assertEquals(3, result.find { it.id == "a" }?.seat)
        // b gets seat 1 (lowest available since 3 is taken, connected.size=2 so available=[1,2])
        assertEquals(1, result.find { it.id == "b" }?.seat)
    }

    @Test
    fun `autoAssignSeats clears seat for disconnected devices`() {
        val disconnected = HapticDevice(id = "x", name = "Oro-09", status = DeviceStatus.Disconnected, seat = 2)
        val devices = listOf(device("a", seat = 1), disconnected)
        val result = autoAssignSeats(devices)
        assertNull(result.find { it.id == "x" }?.seat)
    }
}
